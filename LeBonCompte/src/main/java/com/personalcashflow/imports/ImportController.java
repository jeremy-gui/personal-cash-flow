package com.personalcashflow.imports;

import com.personalcashflow.imports.dto.ImportSummaryResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSummaryResponse importOfx(
            @RequestParam Long accountId, @RequestParam MultipartFile file) {
        return importService.importOfx(accountId, file);
    }

    @GetMapping
    public List<ImportSummaryResponse> history() {
        return importService.history();
    }
}
