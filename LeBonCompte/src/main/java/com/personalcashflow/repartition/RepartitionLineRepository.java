package com.personalcashflow.repartition;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepartitionLineRepository extends JpaRepository<RepartitionLine, Long> {

    List<RepartitionLine> findAllByOrderByIdAsc();
}
