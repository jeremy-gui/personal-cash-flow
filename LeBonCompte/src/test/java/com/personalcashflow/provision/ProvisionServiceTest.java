package com.personalcashflow.provision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.provision.dto.ProvisionContributionRequest;
import com.personalcashflow.provision.dto.ProvisionRequest;
import com.personalcashflow.provision.dto.ProvisionResponse;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionServiceTest {

    @Mock
    private ProvisionRepository provisionRepository;

    @Mock
    private ProvisionContributionRepository contributionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ProvisionService provisionService;

    @Test
    void create_shouldSaveWithoutCategoryOrAccount() {
        when(provisionRepository.save(any(Provision.class))).thenAnswer(i -> i.getArgument(0));
        when(contributionRepository.findByProvisionId(any())).thenReturn(List.of());

        ProvisionRequest request =
                new ProvisionRequest("Taxe foncière 2026", BigDecimal.valueOf(2400), LocalDate.of(2026, 10, 1), null, null, true);

        ProvisionResponse response = provisionService.create(request);

        assertThat(response.name()).isEqualTo("Taxe foncière 2026");
        assertThat(response.active()).isTrue();
        assertThat(response.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_defaultsActiveToTrueWhenOmitted() {
        when(provisionRepository.save(any(Provision.class))).thenAnswer(i -> i.getArgument(0));
        when(contributionRepository.findByProvisionId(any())).thenReturn(List.of());

        ProvisionRequest request =
                new ProvisionRequest("Vacances", BigDecimal.valueOf(1000), null, null, null, null);

        ProvisionResponse response = provisionService.create(request);

        assertThat(response.active()).isTrue();
    }

    @Test
    void update_whenProvisionMissing_shouldThrowNotFound() {
        when(provisionRepository.findById(99L)).thenReturn(Optional.empty());

        ProvisionRequest request = new ProvisionRequest("X", BigDecimal.TEN, null, null, null, true);

        assertThatThrownBy(() -> provisionService.update(99L, request))
                .isInstanceOf(ProvisionNotFoundException.class);
    }

    @Test
    void delete_shouldRemoveTheProvisionAndItsContributions() {
        Provision provision = provisionWithId(1L, "Taxe foncière", BigDecimal.valueOf(2400), null);
        when(provisionRepository.findById(1L)).thenReturn(Optional.of(provision));
        when(contributionRepository.findByProvisionId(1L)).thenReturn(List.of());

        provisionService.delete(1L);

        verify(provisionRepository).delete(provision);
    }

    @Test
    void findAll_shouldComputeCurrentAmountFromContributions() {
        Provision provision = provisionWithId(1L, "Taxe foncière", BigDecimal.valueOf(2400), LocalDate.of(2026, 10, 1));
        when(provisionRepository.findAll()).thenReturn(List.of(provision));
        when(contributionRepository.findByProvisionId(1L)).thenReturn(List.of(
                contribution(provision, BigDecimal.valueOf(1000), LocalDate.of(2026, 1, 1)),
                contribution(provision, BigDecimal.valueOf(600), LocalDate.of(2026, 6, 1))));

        var responses = provisionService.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).currentAmount()).isEqualByComparingTo(BigDecimal.valueOf(1600));
        assertThat(responses.get(0).remaining()).isEqualByComparingTo(BigDecimal.valueOf(800));
        assertThat(responses.get(0).percentageComplete()).isEqualByComparingTo(BigDecimal.valueOf(66.7));
    }

    @Test
    void findAll_withATargetDateAndRemainingAmount_shouldComputeMonthlyEffortNeeded() {
        LocalDate targetDate = YearMonth.now().plusMonths(4).atDay(1);
        Provision provision = provisionWithId(1L, "Taxe foncière", BigDecimal.valueOf(2400), targetDate);
        when(provisionRepository.findAll()).thenReturn(List.of(provision));
        when(contributionRepository.findByProvisionId(1L))
                .thenReturn(List.of(contribution(provision, BigDecimal.valueOf(400), LocalDate.now())));

        var response = provisionService.findAll().get(0);

        // remaining = 2000, 4 months away -> 500/month.
        assertThat(response.monthlyEffortNeeded()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.atRisk()).isFalse();
    }

    @Test
    void findAll_whenTargetDateHasPassedAndAmountIsShort_shouldBeAtRisk() {
        LocalDate pastDate = YearMonth.now().minusMonths(1).atDay(1);
        Provision provision = provisionWithId(1L, "Assurance", BigDecimal.valueOf(500), pastDate);
        when(provisionRepository.findAll()).thenReturn(List.of(provision));
        when(contributionRepository.findByProvisionId(1L)).thenReturn(List.of());

        var response = provisionService.findAll().get(0);

        assertThat(response.atRisk()).isTrue();
        assertThat(response.monthlyEffortNeeded()).isNull();
    }

    @Test
    void findAll_whenTargetAlreadyMet_shouldNotBeAtRiskAndNeedNoFurtherEffort() {
        LocalDate targetDate = YearMonth.now().plusMonths(2).atDay(1);
        Provision provision = provisionWithId(1L, "Cadeaux", BigDecimal.valueOf(200), targetDate);
        when(provisionRepository.findAll()).thenReturn(List.of(provision));
        when(contributionRepository.findByProvisionId(1L))
                .thenReturn(List.of(contribution(provision, BigDecimal.valueOf(250), LocalDate.now())));

        var response = provisionService.findAll().get(0);

        assertThat(response.remaining()).isEqualByComparingTo(BigDecimal.valueOf(-50));
        assertThat(response.atRisk()).isFalse();
        assertThat(response.monthlyEffortNeeded()).isNull();
    }

    @Test
    void findAll_withoutATargetDate_shouldNeverBeAtRisk() {
        Provision provision = provisionWithId(1L, "Entretien voiture", BigDecimal.valueOf(300), null);
        when(provisionRepository.findAll()).thenReturn(List.of(provision));
        when(contributionRepository.findByProvisionId(1L)).thenReturn(List.of());

        var response = provisionService.findAll().get(0);

        assertThat(response.atRisk()).isFalse();
        assertThat(response.monthlyEffortNeeded()).isNull();
    }

    @Test
    void addContribution_shouldSaveItAgainstTheProvision() {
        Provision provision = provisionWithId(1L, "Taxe foncière", BigDecimal.valueOf(2400), null);
        when(provisionRepository.findById(1L)).thenReturn(Optional.of(provision));
        when(contributionRepository.save(any(ProvisionContribution.class))).thenAnswer(i -> i.getArgument(0));

        var response = provisionService.addContribution(1L, new ProvisionContributionRequest(BigDecimal.valueOf(200), LocalDate.now()));

        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    void deleteContribution_whenMissing_shouldThrowNotFound() {
        Provision provision = provisionWithId(1L, "Taxe foncière", BigDecimal.valueOf(2400), null);
        when(provisionRepository.findById(1L)).thenReturn(Optional.of(provision));
        when(contributionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisionService.deleteContribution(1L, 42L))
                .isInstanceOf(ProvisionContributionNotFoundException.class);
    }

    private static Provision provisionWithId(Long id, String name, BigDecimal targetAmount, LocalDate targetDate) {
        Provision provision = new Provision(name, targetAmount, targetDate, null, null);
        setId(Provision.class, provision, id);
        return provision;
    }

    private static ProvisionContribution contribution(Provision provision, BigDecimal amount, LocalDate date) {
        return new ProvisionContribution(provision, amount, date);
    }

    private static void setId(Class<?> type, Object instance, Long id) {
        try {
            Field field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(instance, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
