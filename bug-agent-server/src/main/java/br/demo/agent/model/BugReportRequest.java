package br.demo.agent.model;

import java.util.List;

public record BugReportRequest(
        String stackTrace,
        String uri,
        List<String> allowlist
) {
}
