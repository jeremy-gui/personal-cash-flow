package com.personalcashflow.repartition;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.dashboard.NoDefaultAccountException;
import com.personalcashflow.repartition.dto.AverageFraisResponse;
import com.personalcashflow.repartition.dto.RepartitionLineRequest;
import com.personalcashflow.repartition.dto.RepartitionLineResponse;
import com.personalcashflow.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RepartitionLineService {

    private final RepartitionLineRepository repository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public RepartitionLineService(
            RepartitionLineRepository repository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * The default account's average monthly net frais over this year's complete months (January up
     * to last month) - a real-life reference to compare the planned frais against. Null in January,
     * when no month of the year is complete yet.
     */
    @Transactional(readOnly = true)
    public AverageFraisResponse averageFrais(YearMonth currentMonth) {
        int months = currentMonth.getMonthValue() - 1;
        if (months == 0) {
            return null;
        }
        Account account = accountRepository.findByIsDefaultTrue().orElseThrow(NoDefaultAccountException::new);
        YearMonth first = YearMonth.of(currentMonth.getYear(), 1);
        YearMonth last = currentMonth.minusMonths(1);
        BigDecimal total = transactionRepository.sumNetFrais(account.getId(), first.atDay(1), last.atEndOfMonth());
        return new AverageFraisResponse(
                months, first.toString(), last.toString(), total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP));
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
