package br.demo.mcp.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CodebaseMcpTools {

    private static final String TEST_DIR_PREFIX = "src/test/java/br/demo/matricula/";

    private final Path targetAppRoot;
    private final Set<String> allowlist;

    public CodebaseMcpTools(
            @Value("${mcp.target-app.path}") String targetAppPath,
            @Value("${mcp.codebase.allowlist}") List<String> allowlist) {
        this.targetAppRoot = Path.of(targetAppPath).toAbsolutePath().normalize();
        this.allowlist = allowlist.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizarRelativo)
                .collect(Collectors.toSet());
        log.info("[MCP Server] Escopo carregado | root={} | allowlist={}", this.targetAppRoot, this.allowlist);
    }

    @Tool(description = "Lê o conteúdo de um arquivo Java permitido no projeto target-app a partir do caminho relativo (ex: src/main/java/br/demo/matricula/MatriculaService.java).")
    public String lerArquivo(
            @ToolParam(description = "Caminho relativo do arquivo dentro de target-app") String caminhoRelativo) {
        String normalizado = normalizarRelativo(caminhoRelativo);
        log.info("[MCP TOOL lerArquivo] Tentando ler: {}", normalizado);

        if (!podeAcessar(normalizado)) {
            log.warn("[MCP BLOQUEADO] Tentativa de leitura fora da allowlist: {}", normalizado);
            return "ERRO: Acesso negado. Arquivo fora do escopo permitido.";
        }

        Path arquivo = resolverSeguro(normalizado);
        if (arquivo == null || !Files.exists(arquivo)) {
            return "ERRO: Arquivo não encontrado: " + normalizado;
        }

        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            return "ERRO ao ler arquivo: " + e.getMessage();
        }
    }

    @Tool(description = "Cria ou sobrescreve um arquivo dentro da allowlist ou na pasta de testes src/test/java/br/demo/matricula/.")
    public String escreverArquivo(
            @ToolParam(description = "Caminho relativo dentro de target-app") String caminhoRelativo,
            @ToolParam(description = "Conteúdo completo do arquivo") String conteudo) {
        String normalizado = normalizarRelativo(caminhoRelativo);
        log.info("[MCP TOOL escreverArquivo] Tentando gravar: {}", normalizado);

        if (!podeAcessar(normalizado)) {
            log.warn("[MCP BLOQUEADO] Tentativa de escrita fora da allowlist: {}", normalizado);
            return "ERRO: Acesso negado. Não é permitido modificar arquivos fora do escopo.";
        }

        Path arquivo = resolverSeguro(normalizado);
        if (arquivo == null) {
            return "ERRO: Caminho inválido.";
        }

        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, conteudo);
            return "SUCESSO: Arquivo gravado em " + normalizado;
        } catch (IOException e) {
            return "ERRO ao gravar arquivo: " + e.getMessage();
        }
    }

    @Tool(description = "Executa um teste JUnit no projeto target-app via Maven e retorna as últimas linhas do resultado.")
    public String rodarTeste(
            @ToolParam(description = "Nome da classe de teste (ex: MatriculaServiceTest)") String nomeClasseTeste) {
        if (nomeClasseTeste == null || !nomeClasseTeste.matches("^[A-Za-z0-9_.$#]+$")) {
            return "ERRO: Nome de classe de teste inválido.";
        }

        log.info("[MCP TOOL rodarTeste] Executando teste: {}", nomeClasseTeste);

        try {
            Path mvnw = targetAppRoot.getParent().resolve("mvnw");
            ProcessBuilder pb = new ProcessBuilder(mvnw.toString(), "test", "-Dtest=" + nomeClasseTeste);
            pb.directory(targetAppRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "ERRO: Timeout de 60s ao executar teste.";
            }

            List<String> lines = output.lines().toList();
            int start = Math.max(0, lines.size() - 50);
            String truncated = String.join("\n", lines.subList(start, lines.size()));

            return "EXIT_CODE=" + process.exitValue() + "\n" + truncated;
        } catch (Exception e) {
            return "ERRO ao executar Maven: " + e.getMessage();
        }
    }

    private boolean podeAcessar(String normalizado) {
        return allowlist.contains(normalizado) || normalizado.startsWith(TEST_DIR_PREFIX);
    }

    private String normalizarRelativo(String caminho) {
        return Path.of(caminho.replace("\\", "/"))
                .normalize().toString().replace("\\", "/");
    }

    private Path resolverSeguro(String normalizado) {
        if (normalizado.startsWith("/") || normalizado.startsWith("..")) {
            return null;
        }
        Path resolved = targetAppRoot.resolve(normalizado).normalize();
        return resolved.startsWith(targetAppRoot) ? resolved : null;
    }
}
