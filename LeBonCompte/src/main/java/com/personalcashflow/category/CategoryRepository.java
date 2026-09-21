package com.personalcashflow.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentId(Long parentId);

    List<Category> findByParentId(Long parentId);
}
