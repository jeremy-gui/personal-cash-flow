package com.personalcashflow.recurring;

import com.personalcashflow.recurring.dto.RecurringAmountRequest;
import com.personalcashflow.recurring.dto.RecurringTransactionRequest;
import com.personalcashflow.recurring.dto.RecurringTransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public List<RecurringTransactionResponse> findAll() {
        return recurringTransactionService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringTransactionResponse create(@Valid @RequestBody RecurringTransactionRequest request) {
        return recurringTransactionService.create(request);
    }

    @PutMapping("/{id}/amount")
    public RecurringTransactionResponse updateAmount(
            @PathVariable Long id, @Valid @RequestBody RecurringAmountRequest request) {
        return recurringTransactionService.updateAmount(id, request.amount());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recurringTransactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
