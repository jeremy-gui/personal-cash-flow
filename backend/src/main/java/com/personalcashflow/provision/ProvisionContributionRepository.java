package com.personalcashflow.provision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvisionContributionRepository extends JpaRepository<ProvisionContribution, Long> {

    List<ProvisionContribution> findByProvisionId(Long provisionId);

    List<ProvisionContribution> findByProvisionIdOrderByDateDesc(Long provisionId);
}
