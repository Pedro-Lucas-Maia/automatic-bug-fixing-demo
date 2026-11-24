package br.demo.mcp.config;

import br.demo.mcp.tools.CodebaseMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider codebaseToolsProvider(CodebaseMcpTools codebaseMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(codebaseMcpTools)
                .build();
    }
}
