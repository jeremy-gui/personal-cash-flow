package com.personalcashflow.transaction;

import com.personalcashflow.recurring.RecurringTransactionService;
import com.personalcashflow.transaction.dto.ConfirmCategoriesRequest;
import com.personalcashflow.transaction.dto.TransactionRequest;
import com.personalcashflow.transaction.dto.TransactionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final RecurringTransactionService recurringTransactionService;

    public TransactionController(TransactionService transactionService, RecurringTransactionService recurringTransactionService) {
        this.transactionService = transactionService;
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public List<TransactionResponse> search(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type) {
        if (month != null) {
            recurringTransactionService.ensureGeneratedForMonth(month);
        }
        return transactionService.search(month, accountId, categoryId, status, type).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(transactionService.create(request));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(transactionService.update(id, request));
    }

    @PostMapping("/{id}/confirm-category")
    public TransactionResponse confirmCategory(@PathVariable Long id) {
        return TransactionResponse.from(transactionService.confirmCategory(id));
    }

    @PostMapping("/confirm-categories")
    public List<TransactionResponse> confirmCategories(@Valid @RequestBody ConfirmCategoriesRequest request) {
        return transactionService.confirmCategories(request.ids()).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        TransactionService.DeletedTransaction deleted = transactionService.delete(id);
        if (deleted.recurringTransactionId() != null) {
            recurringTransactionService.recordSkip(deleted.recurringTransactionId(), deleted.date());
        }
        return ResponseEntity.noContent().build();
    }
}
