package com.personalcashflow.budget;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByMonth(LocalDate month);

    boolean existsByCategoryIdAndMonth(Long categoryId, LocalDate month);

    boolean existsByCategoryIdAndMonthAndIdNot(Long categoryId, LocalDate month, Long id);
}
