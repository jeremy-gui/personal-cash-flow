package com.personalcashflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.dto.AccountRequest;
import java.lang.reflect.Field;
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
        AccountRequest request =
                new AccountRequest("Compte courant", AccountType.COURANT, BigDecimal.TEN, "12345", false);
        when(accountRepository.count()).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = accountService.create(request);

        assertThat(created.getName()).isEqualTo("Compte courant");
        assertThat(created.getType()).isEqualTo(AccountType.COURANT);
        assertThat(created.getInitialBalance()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(created.getAccountNumber()).isEqualTo("12345");
    }

    @Test
    void create_withAlreadyUsedAccountNumber_shouldThrowAccountNumberAlreadyUsedException() {
        Account existing = new Account("Livret", AccountType.LIVRET, BigDecimal.ZERO, "12345");
        setId(existing, 5L);
        when(accountRepository.findByAccountNumber("12345")).thenReturn(Optional.of(existing));

        AccountRequest request = new AccountRequest("Compte courant", AccountType.COURANT, BigDecimal.TEN, "12345", false);

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(AccountNumberAlreadyUsedException.class);
    }

    @Test
    void update_withOwnUnchangedAccountNumber_shouldNotThrow() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "12345");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.findByAccountNumber("12345")).thenReturn(Optional.of(account));

        AccountRequest request = new AccountRequest("Compte courant", AccountType.COURANT, BigDecimal.TEN, "12345", false);
        Account updated = accountService.update(1L, request);

        assertThat(updated.getAccountNumber()).isEqualTo("12345");
    }

    @Test
    void create_whenFirstAccount_shouldBecomeDefaultAutomatically() {
        AccountRequest request = new AccountRequest("Compte courant", AccountType.COURANT, BigDecimal.TEN, null, false);
        when(accountRepository.count()).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = accountService.create(request);

        assertThat(created.isDefault()).isTrue();
    }

    @Test
    void create_whenNotFirstAccountAndNotRequestedDefault_shouldNotBecomeDefault() {
        AccountRequest request = new AccountRequest("Livret", AccountType.LIVRET, BigDecimal.TEN, null, false);
        when(accountRepository.count()).thenReturn(1L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = accountService.create(request);

        assertThat(created.isDefault()).isFalse();
    }

    @Test
    void create_withIsDefaultTrue_shouldUnsetThePreviousDefault() {
        Account existingDefault = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        existingDefault.markDefault();
        AccountRequest request = new AccountRequest("Livret", AccountType.LIVRET, BigDecimal.TEN, null, true);
        when(accountRepository.count()).thenReturn(1L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(existingDefault));

        Account created = accountService.create(request);

        assertThat(created.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
    }

    @Test
    void update_withIsDefaultTrue_shouldTransferDefaultFromPreviousAccount() {
        Account existing = new Account("Livret", AccountType.LIVRET, BigDecimal.TEN, null);
        Account previousDefault = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        previousDefault.markDefault();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(previousDefault));

        AccountRequest request = new AccountRequest("Livret", AccountType.LIVRET, BigDecimal.TEN, null, true);
        Account updated = accountService.update(2L, request);

        assertThat(updated.isDefault()).isTrue();
        assertThat(previousDefault.isDefault()).isFalse();
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

    private static void setId(Account account, Long id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
