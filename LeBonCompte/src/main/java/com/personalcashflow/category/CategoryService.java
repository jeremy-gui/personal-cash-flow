package com.personalcashflow.category;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.dto.CategoryRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public CategoryService(CategoryRepository categoryRepository, AccountRepository accountRepository) {
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    public Category create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        Account defaultTransferAccount = resolveAccount(request.defaultTransferAccountId());
        Category category = new Category(request.name(), parent, defaultTransferAccount);
        return categoryRepository.save(category);
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = findById(id);
        if (request.parentId() != null) {
            assertNoCycle(id, request.parentId());
        }
        Category parent = resolveParent(request.parentId());
        Account defaultTransferAccount = resolveAccount(request.defaultTransferAccountId());
        category.update(request.name(), parent, defaultTransferAccount);
        return category;
    }

    public void delete(Long id) {
        Category category = findById(id);
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new CategoryHasChildrenException(id);
        }
        categoryRepository.delete(category);
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return findById(parentId);
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private void assertNoCycle(Long categoryId, Long proposedParentId) {
        Long current = proposedParentId;
        while (current != null) {
            if (current.equals(categoryId)) {
                throw new CategoryCycleException(categoryId, proposedParentId);
            }
            Category parent = findById(current).getParent();
            current = parent != null ? parent.getId() : null;
        }
    }
}
