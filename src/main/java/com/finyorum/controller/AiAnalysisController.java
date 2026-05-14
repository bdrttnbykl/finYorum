package com.finyorum.controller;

import com.finyorum.dto.AiAnalysisRequest;
import com.finyorum.dto.AiAnalysisResponse;
import com.finyorum.service.AiAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/analyze")
    AiAnalysisResponse analyze(@Valid @RequestBody AiAnalysisRequest request) {
        return aiAnalysisService.analyze(request.symbol());
    }
}
