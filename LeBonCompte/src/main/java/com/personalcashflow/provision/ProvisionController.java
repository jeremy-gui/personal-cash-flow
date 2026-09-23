package com.personalcashflow.provision;

import com.personalcashflow.provision.dto.ProvisionContributionRequest;
import com.personalcashflow.provision.dto.ProvisionContributionResponse;
import com.personalcashflow.provision.dto.ProvisionRequest;
import com.personalcashflow.provision.dto.ProvisionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provisions")
public class ProvisionController {

    private final ProvisionService provisionService;

    public ProvisionController(ProvisionService provisionService) {
        this.provisionService = provisionService;
    }

    @GetMapping
    public List<ProvisionResponse> findAll() {
        return provisionService.findAll();
    }

    @GetMapping("/{id}")
    public ProvisionResponse findById(@PathVariable Long id) {
        return provisionService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionResponse create(@Valid @RequestBody ProvisionRequest request) {
        return provisionService.create(request);
    }

    @PutMapping("/{id}")
    public ProvisionResponse update(@PathVariable Long id, @Valid @RequestBody ProvisionRequest request) {
        return provisionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        provisionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contributions")
    public List<ProvisionContributionResponse> findContributions(@PathVariable Long id) {
        return provisionService.findContributions(id);
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionContributionResponse addContribution(
            @PathVariable Long id, @Valid @RequestBody ProvisionContributionRequest request) {
        return provisionService.addContribution(id, request);
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<Void> deleteContribution(@PathVariable Long id, @PathVariable Long contributionId) {
        provisionService.deleteContribution(id, contributionId);
        return ResponseEntity.noContent().build();
    }
}
