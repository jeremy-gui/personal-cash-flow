package com.personalcashflow.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.category.dto.CategoryRequest;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void create_withoutParent_shouldSaveRootCategory() {
        CategoryRequest request = new CategoryRequest("Loisirs", null, null, null);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(request);

        assertThat(created.getName()).isEqualTo("Loisirs");
        assertThat(created.getParent()).isNull();
    }

    @Test
    void create_withoutColor_shouldAutoAssignOneBasedOnExistingRootCount() {
        when(categoryRepository.countByParentIdIsNull()).thenReturn(3L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Loisirs", null, null, null));

        assertThat(created.getColor()).isEqualTo(CategoryColors.next(3L));
    }

    @Test
    void create_withExplicitColor_shouldUseItInsteadOfAutoAssigning() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Loisirs", null, null, "#123456"));

        assertThat(created.getColor()).isEqualTo("#123456");
    }

    @Test
    void create_withParent_shouldResolveParentFromRepository() {
        Category parent = categoryWithId(1L, "Logement", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Électricité", 1L, null, null));

        assertThat(created.getParent()).isEqualTo(parent);
    }

    @Test
    void create_withParent_shouldInheritParentColorIgnoringAnyRequestedColor() {
        Category parent = categoryWithColor(1L, "Logement", null, "#111111");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Électricité", 1L, null, "#abcdef"));

        assertThat(created.getColor()).isEqualTo("#111111");
    }

    @Test
    void create_withDefaultTransferAccount_shouldResolveItFromRepository() {
        Account account = new Account("LDDS", AccountType.LIVRET, BigDecimal.ZERO, null);
        setAccountId(account, 7L);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Provision", null, 7L, null));

        assertThat(created.getDefaultTransferAccount()).isEqualTo(account);
    }

    @Test
    void update_shouldApplyDefaultTransferAccount() {
        Category category = categoryWithId(1L, "Provision", null);
        Account account = new Account("LDDS", AccountType.LIVRET, BigDecimal.ZERO, null);
        setAccountId(account, 7L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));

        Category updated = categoryService.update(1L, new CategoryRequest("Provision", null, 7L, null));

        assertThat(updated.getDefaultTransferAccount()).isEqualTo(account);
    }

    @Test
    void update_withoutColor_shouldKeepTheExistingOne() {
        Category category = categoryWithId(1L, "Provision", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        String originalColor = category.getColor();

        Category updated = categoryService.update(1L, new CategoryRequest("Provision", null, null, null));

        assertThat(updated.getColor()).isEqualTo(originalColor);
    }

    @Test
    void update_withExplicitColor_shouldReplaceTheExistingOne() {
        Category category = categoryWithId(1L, "Provision", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        Category updated = categoryService.update(1L, new CategoryRequest("Provision", null, null, "#abcdef"));

        assertThat(updated.getColor()).isEqualTo("#abcdef");
    }

    @Test
    void update_whenRootColorChanges_shouldCascadeToEveryDescendant() {
        Category root = categoryWithColor(1L, "Logement", null, "#111111");
        Category child = categoryWithColor(2L, "Sous-Logement", root, "#111111");
        Category grandchild = categoryWithColor(3L, "Loyer", child, "#111111");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.findByParentId(1L)).thenReturn(List.of(child));
        when(categoryRepository.findByParentId(2L)).thenReturn(List.of(grandchild));
        when(categoryRepository.findByParentId(3L)).thenReturn(List.of());

        categoryService.update(1L, new CategoryRequest("Logement", null, null, "#abcdef"));

        assertThat(child.getColor()).isEqualTo("#abcdef");
        assertThat(grandchild.getColor()).isEqualTo("#abcdef");
    }

    @Test
    void update_whenReparented_shouldAdoptTheNewParentsColor() {
        Category oldRoot = categoryWithColor(1L, "Logement", null, "#111111");
        Category category = categoryWithColor(2L, "Sous-Logement", oldRoot, "#111111");
        Category newParent = categoryWithColor(3L, "Loisirs", null, "#abcdef");
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(newParent));
        when(categoryRepository.findByParentId(2L)).thenReturn(List.of());

        Category updated = categoryService.update(2L, new CategoryRequest("Sous-Logement", 3L, null, null));

        assertThat(updated.getColor()).isEqualTo("#abcdef");
    }

    @Test
    void findById_whenCategoryNotFound_shouldThrowNotFoundException() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(42L))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void update_withOwnDescendantAsParent_shouldThrowCycleException() {
        Category root = categoryWithId(1L, "Logement", null);
        Category child = categoryWithId(2L, "Sous-Logement", root);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Logement", 2L, null, null)))
                .isInstanceOf(CategoryCycleException.class);
    }

    @Test
    void update_withSelfAsParent_shouldThrowCycleException() {
        Category category = categoryWithId(1L, "Logement", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Logement", 1L, null, null)))
                .isInstanceOf(CategoryCycleException.class);
    }

    @Test
    void delete_whenCategoryHasChildren_shouldThrowConflictException() {
        Category category = categoryWithId(1L, "Logement", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(CategoryHasChildrenException.class);
    }

    @Test
    void delete_whenCategoryHasNoChildren_shouldRemoveIt() {
        Category category = categoryWithId(1L, "Loisirs", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(categoryRepository.countUsages(1L)).thenReturn(usage(0, 0, 0, 0));

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_whenCategoryIsStillUsed_shouldExplainWhatUsesIt() {
        Category category = categoryWithId(1L, "Loisirs", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(categoryRepository.countUsages(1L)).thenReturn(usage(12, 1, 0, 2));

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(CategoryInUseException.class)
                .hasMessage("Impossible de supprimer « Loisirs » : elle est encore utilisée par 12 transactions, "
                        + "1 budget et 2 transactions récurrentes. Change leur catégorie ou supprime-les d'abord.");
        verify(categoryRepository, never()).delete(category);
    }

    private static CategoryRepository.CategoryUsage usage(long transactions, long budgets, long provisions, long recurrings) {
        return new CategoryRepository.CategoryUsage() {
            public long getTransactions() { return transactions; }
            public long getBudgets() { return budgets; }
            public long getProvisions() { return provisions; }
            public long getRecurrings() { return recurrings; }
        };
    }

    /** Sets the generated id (and a placeholder color) via reflection: the entity has no public
     *  id-setting constructor, and the color-less constructor leaves color null. */
    private static Category categoryWithId(Long id, String name, Category parent) {
        return categoryWithColor(id, name, parent, "#111111");
    }

    private static Category categoryWithColor(Long id, String name, Category parent, String color) {
        Category category = new Category(name, parent);
        try {
            Field idField = Category.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
            Field colorField = Category.class.getDeclaredField("color");
            colorField.setAccessible(true);
            colorField.set(category, color);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return category;
    }

    private static void setAccountId(Account account, Long id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
