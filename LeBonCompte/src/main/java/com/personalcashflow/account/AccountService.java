package com.personalcashflow.account;

import com.personalcashflow.account.dto.AccountRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public Account create(AccountRequest request) {
        String accountNumber = normalize(request.accountNumber());
        ensureAccountNumberAvailable(accountNumber, null);
        Account account = new Account(request.name(), request.type(), request.initialBalance(), accountNumber);
        boolean isFirstAccount = accountRepository.count() == 0;
        Account saved = accountRepository.save(account);
        if (request.isDefault() || isFirstAccount) {
            assignDefault(saved);
        }
        return saved;
    }

    public Account update(Long id, AccountRequest request) {
        Account account = findById(id);
        String accountNumber = normalize(request.accountNumber());
        ensureAccountNumberAvailable(accountNumber, id);
        account.update(request.name(), request.type(), request.initialBalance(), accountNumber);
        if (request.isDefault() && !account.isDefault()) {
            assignDefault(account);
        } else if (!request.isDefault() && account.isDefault()) {
            account.unmarkDefault();
        }
        return account;
    }

    /** The DB has a unique constraint on account_number too; this just gives a clean error instead of a 500. */
    private void ensureAccountNumberAvailable(String accountNumber, Long excludingId) {
        if (accountNumber == null) {
            return;
        }
        accountRepository.findByAccountNumber(accountNumber)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new AccountNumberAlreadyUsedException(accountNumber);
                });
    }

    public void delete(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        accountRepository.deleteById(id);
    }

    /**
     * Only one account may be default at a time. The partial unique index on is_default is
     * checked per-statement, not deferred, so the old default's UPDATE must be flushed before
     * the new one is marked — otherwise both rows can briefly read as default in the same flush
     * batch and the constraint rejects it.
     */
    private void assignDefault(Account account) {
        accountRepository.findByIsDefaultTrue().ifPresent(previous -> {
            previous.unmarkDefault();
            accountRepository.flush();
        });
        account.markDefault();
    }

    private static String normalize(String accountNumber) {
        return (accountNumber == null || accountNumber.isBlank()) ? null : accountNumber.trim();
    }
}
