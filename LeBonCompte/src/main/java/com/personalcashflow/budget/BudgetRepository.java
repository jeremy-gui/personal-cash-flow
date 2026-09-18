package com.personalcashflow.budget;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByStartMonthLessThanEqual(LocalDate month);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndIdNot(Long categoryId, Long id);
}
