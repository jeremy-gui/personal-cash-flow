package com.personalcashflow.dashboard;

import com.personalcashflow.dashboard.dto.DashboardResponse;
import com.personalcashflow.recurring.RecurringTransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RecurringTransactionService recurringTransactionService;

    public DashboardController(DashboardService dashboardService, RecurringTransactionService recurringTransactionService) {
        this.dashboardService = dashboardService;
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public DashboardResponse get(@RequestParam String month) {
        recurringTransactionService.ensureGeneratedForMonth(month);
        return dashboardService.getDashboard(month);
    }
}
