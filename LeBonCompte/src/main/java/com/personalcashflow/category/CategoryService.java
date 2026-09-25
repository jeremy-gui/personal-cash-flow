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

    /** Allowed types are only kept on a root category: its sub-categories always inherit them (like the color). */
    public Category create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        Account defaultTransferAccount = resolveAccount(request.defaultTransferAccountId());
        String color = resolveColor(parent, request.color(), null);
        Category category = new Category(request.name(), parent, defaultTransferAccount, color);
        category.changeAllowedTypes(parent == null ? request.allowedTypes() : null);
        return categoryRepository.save(category);
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = findById(id);
        if (request.parentId() != null) {
            assertNoCycle(id, request.parentId());
        }
        Category parent = resolveParent(request.parentId());
        Account defaultTransferAccount = resolveAccount(request.defaultTransferAccountId());
        String previousColor = category.getColor();
        String color = resolveColor(parent, request.color(), previousColor);
        category.update(request.name(), parent, defaultTransferAccount, color);
        category.changeAllowedTypes(parent == null ? request.allowedTypes() : null);
        if (!color.equals(previousColor)) {
            cascadeColorToDescendants(category, color);
        }
        return category;
    }

    /**
     * A category's color always matches its root ancestor's: a category with a parent inherits
     * that parent's current color (a request color is ignored - subcategories never diverge from
     * their root), and a root either keeps its requested/current color or gets a fresh
     * auto-assigned one when neither is available.
     */
    private String resolveColor(Category parent, String requestedColor, String currentColor) {
        if (parent != null) {
            return parent.getColor();
        }
        if (requestedColor != null) {
            return requestedColor;
        }
        return currentColor != null ? currentColor : CategoryColors.next(categoryRepository.countByParentIdIsNull());
    }

    private void cascadeColorToDescendants(Category category, String color) {
        for (Category child : categoryRepository.findByParentId(category.getId())) {
            child.applyColor(color);
            cascadeColorToDescendants(child, color);
        }
    }

    public void delete(Long id) {
        Category category = findById(id);
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new CategoryHasChildrenException(category.getName());
        }
        CategoryRepository.CategoryUsage usage = categoryRepository.countUsages(category.getId());
        if (CategoryInUseException.isUsed(usage)) {
            throw new CategoryInUseException(category.getName(), usage);
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
