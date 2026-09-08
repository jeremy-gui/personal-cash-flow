package com.personalcashflow.category;

import com.personalcashflow.category.dto.CategoryRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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
        Category category = new Category(request.name(), parent);
        return categoryRepository.save(category);
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = findById(id);
        if (request.parentId() != null) {
            assertNoCycle(id, request.parentId());
        }
        Category parent = resolveParent(request.parentId());
        category.update(request.name(), parent);
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
