package com.personalcashflow.repartition;

import com.personalcashflow.repartition.dto.RepartitionLineRequest;
import com.personalcashflow.repartition.dto.RepartitionLineResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RepartitionLineService {

    private final RepartitionLineRepository repository;

    public RepartitionLineService(RepartitionLineRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RepartitionLineResponse> findAll() {
        return repository.findAllByOrderByIdAsc().stream().map(RepartitionLineResponse::of).toList();
    }

    public RepartitionLineResponse create(RepartitionLineRequest request) {
        return RepartitionLineResponse.of(
                repository.save(new RepartitionLine(request.kind(), request.label(), request.amount())));
    }

    public RepartitionLineResponse update(Long id, RepartitionLineRequest request) {
        RepartitionLine line = findEntity(id);
        line.update(request.kind(), request.label(), request.amount());
        return RepartitionLineResponse.of(line);
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private RepartitionLine findEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new RepartitionLineNotFoundException(id));
    }
}
