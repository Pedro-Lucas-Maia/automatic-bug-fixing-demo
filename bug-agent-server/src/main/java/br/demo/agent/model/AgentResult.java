package br.demo.agent.model;

public record AgentResult(
        String resumoAgente,
        String gitDiff,
        String testeGerado,
        int totalChamadasTools,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
