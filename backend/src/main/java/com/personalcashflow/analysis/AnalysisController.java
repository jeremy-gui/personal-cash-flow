package com.personalcashflow.analysis;

import com.personalcashflow.analysis.dto.YearlyAnalysisResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/yearly")
    public YearlyAnalysisResponse yearly(@RequestParam int year) {
        return analysisService.getYearlyAnalysis(year);
    }
}
