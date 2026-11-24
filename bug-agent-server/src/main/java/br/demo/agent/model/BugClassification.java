package br.demo.agent.model;

public record BugClassification(
        String modulo,
        String camada,
        String tipoExcecao,
        String categoriaCausa,
        String resumo
) {
}
