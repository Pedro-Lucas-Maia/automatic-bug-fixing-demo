package br.demo.agent.service;

import br.demo.agent.model.BugClassification;
import br.demo.agent.model.BugReportRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BugClassifierService {
    private static final String SYSTEM_PROMPT = """
            Você é um classificador de erros de aplicações Java/Spring.
            Analise a URI e o stack trace fornecidos e classifique o erro
            usando APENAS os valores permitidos:
            - módulo: MATRICULA, FINANCEIRO, OUTRO
            - camada: CONTROLLER, SERVICE, REPOSITORY, DESCONHECIDA
            - categoriaCausa: NULL_POINTER, VALIDACAO, REGRA_NEGOCIO, INTEGRACAO, OUTRO
            -tipoExcecao: nome simples da exceção principal (ex.: NullPointerException)
            -resumo: explicação curta em português (máximo 1 frase).
            """;
    private final ChatClient chatClient;

    public BugClassifierService(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    public BugClassification classify(BugReportRequest request) {
        var responseEntity = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        URI: %s
                        Stack trace: 
                        %s
                        """.formatted(request.uri(), request.stackTrace()))
                .call()
                .responseEntity(BugClassification.class);

        log.info("Tokens classificação: {}",
                responseEntity.getResponse().getMetadata().getUsage());
        return responseEntity.getEntity();
    }
}
