package com.personalcashflow.repartition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.transaction.TransactionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepartitionLineServiceTest {

    @Mock
    private RepartitionLineRepository repository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private RepartitionLineService service;

    @Test
    void averageFrais_shouldAverageTheYearsCompleteMonths() {
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(accountWithId(1L)));
        when(transactionRepository.sumNetFrais(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(BigDecimal.valueOf(44000));

        var average = service.averageFrais(YearMonth.of(2026, 9));

        assertThat(average.months()).isEqualTo(8);
        assertThat(average.from()).isEqualTo("2026-01");
        assertThat(average.to()).isEqualTo("2026-08");
        assertThat(average.average()).isEqualByComparingTo("5500");
    }

    @Test
    void averageFrais_inJanuary_shouldHaveNoCompleteMonth() {
        assertThat(service.averageFrais(YearMonth.of(2026, 1))).isNull();
        verifyNoInteractions(transactionRepository);
    }

    private static Account accountWithId(Long id) {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        try {
            Field field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return account;
    }
}
