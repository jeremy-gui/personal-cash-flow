package com.personalcashflow.provision;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.provision.dto.ProvisionContributionRequest;
import com.personalcashflow.provision.dto.ProvisionContributionResponse;
import com.personalcashflow.provision.dto.ProvisionRequest;
import com.personalcashflow.provision.dto.ProvisionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responses are built inside these transactional methods, not by the controller afterward:
 * {@code open-in-view} is disabled, so mapping entities to DTOs after the session has closed
 * throws {@code LazyInitializationException} on any lazy field (category/account here) - the same
 * rule {@code DashboardService}/{@code ReconciliationService} already follow.
 */
@Service
@Transactional
public class ProvisionService {

    private final ProvisionRepository provisionRepository;
    private final ProvisionContributionRepository contributionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public ProvisionService(
            ProvisionRepository provisionRepository,
            ProvisionContributionRepository contributionRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository) {
        this.provisionRepository = provisionRepository;
        this.contributionRepository = contributionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<ProvisionResponse> findAll() {
        return provisionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProvisionResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    public ProvisionResponse create(ProvisionRequest request) {
        Category category = resolveCategory(request.categoryId());
        Account account = resolveAccount(request.accountId());
        Provision provision = new Provision(
                request.name(), request.targetAmount(), request.targetDate(), category, account);
        provisionRepository.save(provision);
        return toResponse(provision);
    }

    public ProvisionResponse update(Long id, ProvisionRequest request) {
        Provision provision = findEntity(id);
        Category category = resolveCategory(request.categoryId());
        Account account = resolveAccount(request.accountId());
        provision.update(
                request.name(), request.targetAmount(), request.targetDate(), category, account, request.activeOrDefault());
        return toResponse(provision);
    }

    public void delete(Long id) {
        Provision provision = findEntity(id);
        contributionRepository.deleteAll(contributionRepository.findByProvisionId(id));
        provisionRepository.delete(provision);
    }

    @Transactional(readOnly = true)
    public List<ProvisionContributionResponse> findContributions(Long provisionId) {
        findEntity(provisionId);
        return contributionRepository.findByProvisionIdOrderByDateDesc(provisionId).stream()
                .map(ProvisionContributionResponse::from)
                .toList();
    }

    public ProvisionContributionResponse addContribution(Long provisionId, ProvisionContributionRequest request) {
        Provision provision = findEntity(provisionId);
        ProvisionContribution contribution = new ProvisionContribution(provision, request.amount(), request.date());
        contributionRepository.save(contribution);
        return ProvisionContributionResponse.from(contribution);
    }

    public void deleteContribution(Long provisionId, Long contributionId) {
        findEntity(provisionId);
        ProvisionContribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new ProvisionContributionNotFoundException(contributionId));
        contributionRepository.delete(contribution);
    }

    private Provision findEntity(Long id) {
        return provisionRepository.findById(id).orElseThrow(() -> new ProvisionNotFoundException(id));
    }

    private ProvisionResponse toResponse(Provision provision) {
        return ProvisionResponse.of(provision, contributionRepository.findByProvisionId(provision.getId()));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
