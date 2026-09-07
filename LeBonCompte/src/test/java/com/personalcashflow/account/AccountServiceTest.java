package com.personalcashflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.dto.AccountRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    @Test
    void createsAccountFromRequest() {
        AccountRequest request = new AccountRequest("Compte courant", AccountType.COURANT, BigDecimal.TEN);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = accountService.create(request);

        assertThat(created.getName()).isEqualTo("Compte courant");
        assertThat(created.getType()).isEqualTo(AccountType.COURANT);
        assertThat(created.getInitialBalance()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(accountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findById(42L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(accountRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.delete(42L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingAccount() {
        when(accountRepository.existsById(1L)).thenReturn(true);

        accountService.delete(1L);

        verify(accountRepository).deleteById(1L);
    }
}
