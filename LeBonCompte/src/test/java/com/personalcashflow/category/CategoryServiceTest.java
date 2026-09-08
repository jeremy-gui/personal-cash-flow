package com.personalcashflow.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.category.dto.CategoryRequest;
import java.lang.reflect.Field;
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

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void create_withoutParent_shouldSaveRootCategory() {
        CategoryRequest request = new CategoryRequest("Loisirs", null);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(request);

        assertThat(created.getName()).isEqualTo("Loisirs");
        assertThat(created.getParent()).isNull();
    }

    @Test
    void create_withParent_shouldResolveParentFromRepository() {
        Category parent = categoryWithId(1L, "Logement", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create(new CategoryRequest("Électricité", 1L));

        assertThat(created.getParent()).isEqualTo(parent);
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

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Logement", 2L)))
                .isInstanceOf(CategoryCycleException.class);
    }

    @Test
    void update_withSelfAsParent_shouldThrowCycleException() {
        Category category = categoryWithId(1L, "Logement", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Logement", 1L)))
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

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    /** Sets the generated id via reflection since the entity has no public id-setting constructor. */
    private static Category categoryWithId(Long id, String name, Category parent) {
        Category category = new Category(name, parent);
        try {
            Field idField = Category.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return category;
    }
}
