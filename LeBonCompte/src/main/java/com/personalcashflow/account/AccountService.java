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
        Account account = new Account(request.name(), request.type(), request.initialBalance());
        return accountRepository.save(account);
    }

    public Account update(Long id, AccountRequest request) {
        Account account = findById(id);
        account.update(request.name(), request.type(), request.initialBalance());
        return account;
    }

    public void delete(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        accountRepository.deleteById(id);
    }
}
