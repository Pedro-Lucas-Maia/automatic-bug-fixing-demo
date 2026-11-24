package br.demo.agent.service;

import br.demo.agent.model.AgentResult;
import br.demo.agent.model.BugReportRequest;
import br.demo.agent.tools.ScopedCodeBaseTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BugFixAgentService {

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

    public BugFixAgentService(
            ChatClient.Builder builder,
            @Value("${agent.target-app.path}") String targetAppPath) {
        this.chatClient = builder.build();
        this.targetAppPath = Path.of(targetAppPath).toAbsolutePath().normalize();
    }

    public AgentResult analyzeAndFix(BugReportRequest request) {
        List<String> allowlist = request.allowlist() != null ? request.allowlist() : List.of();
        ScopedCodeBaseTools tools = new ScopedCodeBaseTools(targetAppPath, allowlist);

        String userPrompt = """
                URI: %s
                Arquivos permitidos (allowlist): %s

                Stack trace:
                %s
                """.formatted(request.uri(), allowlist, request.stackTrace());

        var chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .tools(tools)
                .call()
                .chatResponse();

        String resumo = chatResponse.getResult().getOutput().getText();
        var usage = chatResponse.getMetadata().getUsage();

        log.info("Agente finalizado | chamadas={} | tokens={}", tools.getTotalChamadas(), usage);

        String diff = capturarGitDiff();
        String testeGerado = lerTesteGerado(tools.getUltimoTesteCriado());

        return new AgentResult(
                resumo,
                diff,
                testeGerado,
                tools.getTotalChamadas(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null
        );
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
}
