package br.demo.agent.controller;

import br.demo.agent.model.AgentResult;
import br.demo.agent.model.BugClassification;
import br.demo.agent.model.BugReportRequest;
import br.demo.agent.service.BugClassifierService;
import br.demo.agent.service.BugFixAgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BugAgentController {

    private final BugClassifierService bugClassifierService;
    private final BugFixAgentService bugFixAgentService;

    public BugAgentController(
            BugClassifierService bugClassifierService,
            BugFixAgentService bugFixAgentService) {
        this.bugClassifierService = bugClassifierService;
        this.bugFixAgentService = bugFixAgentService;
    }

    @PostMapping("/classify-bug")
    public BugClassification classifyBug(@RequestBody BugReportRequest request) {
        return bugClassifierService.classify(request);
    }

    @PostMapping("/analyze-bug")
    public AgentResult analyzeBug(@RequestBody BugReportRequest request) {
        return bugFixAgentService.analyzeAndFix(request);
    }
}
