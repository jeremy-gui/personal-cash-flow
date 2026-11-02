package com.personalcashflow.account;

import com.personalcashflow.account.dto.AccountRequest;
import com.personalcashflow.account.dto.AccountResponse;
import jakarta.validation.Valid;
import java.time.YearMonth;
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
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** {@code month} (yyyy-MM), when given, returns each balance as of the end of that month. */
    @GetMapping
    public List<AccountResponse> findAll(@RequestParam(required = false) String month) {
        return accountService.findAll().stream()
                .map(account -> AccountResponse.from(account, month == null
                        ? accountService.balance(account)
                        : accountService.balanceAt(account, YearMonth.parse(month).atEndOfMonth())))
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse findById(@PathVariable Long id) {
        Account account = accountService.findById(id);
        return AccountResponse.from(account, accountService.balance(account));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        Account account = accountService.create(request);
        return AccountResponse.from(account, accountService.balance(account));
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        Account account = accountService.update(id, request);
        return AccountResponse.from(account, accountService.balance(account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
