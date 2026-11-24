package br.demo.agent.service;

import br.demo.agent.model.AgentResult;
import br.demo.agent.model.BugReportRequest;
import br.demo.agent.tools.ScopedCodeBaseTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
public class BugFixAgentService {

    private static final int MAX_MCP_CALLS = 10;

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro Java especialista em diagnóstico e correção segura de bugs.
            Siga RIGOROSAMENTE este protocolo:
            1. Use lerArquivo para inspecionar os arquivos da allowlist relacionados ao stack trace.
            2. Crie um teste unitário JUnit 5 (com Mockito se houver dependências como Repository) em
               src/test/java/br/demo/matricula/ que REPRODUZA o erro original.
               O teste deve validar o comportamento correto esperado (ou seja, ANTES da correção ele deve FALHAR com o bug).
            3. Execute rodarTeste para confirmar que o teste falhou reproduzindo o problema.
            4. Aplique a correção MÍNIMA necessária no arquivo Java permitido pela allowlist usando escreverArquivo.
            5. Execute rodarTeste novamente até passar (EXIT_CODE=0).
            6. Se não conseguir reproduzir ou atingir o limite de chamadas, NÃO invente: explique o diagnóstico e a correção sugerida.
            Nunca tente acessar arquivos fora da allowlist.
            """;

    private final ChatClient chatClient;
    private final Path targetAppPath;
    private final ObjectProvider<ToolCallbackProvider> mcpToolCallbackProvider;

    public BugFixAgentService(
            ChatClient.Builder builder,
            @Value("${agent.target-app.path}") String targetAppPath,
            ObjectProvider<ToolCallbackProvider> mcpToolCallbackProvider) {
        this.chatClient = builder.build();
        this.targetAppPath = Path.of(targetAppPath).toAbsolutePath().normalize();
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    public AgentResult analyzeAndFix(BugReportRequest request) {
        List<String> allowlist = request.allowlist() != null ? request.allowlist() : List.of();
        ScopedCodeBaseTools tools = new ScopedCodeBaseTools(targetAppPath, allowlist);

        var chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(montarUserPrompt(request, allowlist))
                .tools(tools)
                .call()
                .chatResponse();

        String resumo = chatResponse.getResult().getOutput().getText();
        var usage = chatResponse.getMetadata().getUsage();

        log.info("Agente (Tools locais) finalizado | chamadas={} | tokens={}", tools.getTotalChamadas(), usage);

        return new AgentResult(
                resumo,
                capturarGitDiff(),
                lerTesteGerado(tools.getUltimoTesteCriado()),
                tools.getTotalChamadas(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null
        );
    }

    public AgentResult analyzeAndFixWithMcp(BugReportRequest request) {
        ToolCallbackProvider provider = mcpToolCallbackProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("MCP ToolCallbackProvider não disponível. Verifique se o codebase-mcp-server está rodando na porta 8081.");
        }

        List<String> allowlist = request.allowlist() != null ? request.allowlist() : List.of();
        AtomicInteger mcpCallCount = new AtomicInteger(0);
        ToolCallback[] wrappedCallbacks = Arrays.stream(provider.getToolCallbacks())
                .map(cb -> wrapWithLimitAndCounter(cb, mcpCallCount))
                .toArray(ToolCallback[]::new);

        var chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(montarUserPrompt(request, allowlist))
                .toolCallbacks(wrappedCallbacks)
                .call()
                .chatResponse();

        String resumo = chatResponse.getResult().getOutput().getText();
        var usage = chatResponse.getMetadata().getUsage();

        log.info("Agente (MCP) finalizado | chamadas={} | tokens={}", mcpCallCount.get(), usage);

        return new AgentResult(
                resumo,
                capturarGitDiff(),
                lerUltimoTesteNaPasta(),
                mcpCallCount.get(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null
        );
    }

    private ToolCallback wrapWithLimitAndCounter(ToolCallback delegate, AtomicInteger counter) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                if (counter.incrementAndGet() > MAX_MCP_CALLS) {
                    return "ERRO: Limite máximo de chamadas (" + MAX_MCP_CALLS + ") atingido. Encerre e apresente seu diagnóstico.";
                }
                return delegate.call(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                if (counter.incrementAndGet() > MAX_MCP_CALLS) {
                    return "ERRO: Limite máximo de chamadas (" + MAX_MCP_CALLS + ") atingido. Encerre e apresente seu diagnóstico.";
                }
                return delegate.call(toolInput, toolContext);
            }
        };
    }

    private String montarUserPrompt(BugReportRequest request, List<String> allowlist) {
        return """
                URI: %s
                Arquivos permitidos (allowlist): %s

                Stack trace:
                %s
                """.formatted(request.uri(), allowlist, request.stackTrace());
    }

    private String capturarGitDiff() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--", "target-app/");
            pb.directory(targetAppPath.getParent().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String diff = new String(process.getInputStream().readAllBytes());
            process.waitFor(15, TimeUnit.SECONDS);
            return diff;
        } catch (Exception e) {
            return "ERRO ao capturar git diff: " + e.getMessage();
        }
    }

    private String lerTesteGerado(Path ultimoTeste) {
        if (ultimoTeste == null || !Files.exists(ultimoTeste)) {
            return "";
        }
        try {
            return Files.readString(ultimoTeste);
        } catch (Exception e) {
            return "ERRO ao ler teste gerado: " + e.getMessage();
        }
    }

    private String lerUltimoTesteNaPasta() {
        Path testDir = targetAppPath.resolve("src/test/java/br/demo/matricula");
        if (!Files.isDirectory(testDir)) {
            return "";
        }
        try (Stream<Path> files = Files.list(testDir)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .findFirst()
                    .map(this::lerTesteGerado)
                    .orElse("");
        } catch (Exception e) {
            return "ERRO ao listar testes gerados: " + e.getMessage();
        }
    }
}
