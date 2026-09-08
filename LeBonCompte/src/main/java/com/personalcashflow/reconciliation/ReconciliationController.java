package com.personalcashflow.reconciliation;

import com.personalcashflow.reconciliation.dto.MatchRequest;
import com.personalcashflow.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions/{id}/match")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    public TransactionResponse match(@PathVariable Long id, @Valid @RequestBody MatchRequest request) {
        return reconciliationService.match(id, request.matchWithId());
    }
}
