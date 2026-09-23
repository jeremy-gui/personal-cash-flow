package com.personalcashflow.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentId(Long parentId);

    List<Category> findByParentId(Long parentId);

    /** Diversity is spread across root categories only - children inherit their root's color, so counting them too would waste hue spacing. */
    long countByParentIdIsNull();

    /**
     * Overrides JpaRepository's default to fetch-join defaultTransferAccount: open-in-view is
     * disabled, so CategoryController maps entities to CategoryResponse (which reads
     * defaultTransferAccount.getName()) after the transactional session has already closed -
     * without this, that lazy field throws LazyInitializationException.
     */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.defaultTransferAccount")
    @Override
    List<Category> findAll();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.defaultTransferAccount WHERE c.id = :id")
    @Override
    Optional<Category> findById(@Param("id") Long id);
}
