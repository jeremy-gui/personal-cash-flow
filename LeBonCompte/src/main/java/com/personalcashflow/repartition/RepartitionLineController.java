package com.personalcashflow.repartition;

import com.personalcashflow.repartition.dto.RepartitionLineRequest;
import com.personalcashflow.repartition.dto.RepartitionLineResponse;
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
@RequestMapping("/api/repartition-lines")
public class RepartitionLineController {

    private final RepartitionLineService service;

    public RepartitionLineController(RepartitionLineService service) {
        this.service = service;
    }

    @GetMapping
    public List<RepartitionLineResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepartitionLineResponse create(@Valid @RequestBody RepartitionLineRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RepartitionLineResponse update(@PathVariable Long id, @Valid @RequestBody RepartitionLineRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
