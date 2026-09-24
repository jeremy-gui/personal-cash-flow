package com.personalcashflow.category;

import com.personalcashflow.common.ConflictException;
import java.util.ArrayList;
import java.util.List;

public class CategoryInUseException extends ConflictException {

    public CategoryInUseException(String name, CategoryRepository.CategoryUsage usage) {
        super("Impossible de supprimer « " + name + " » : elle est encore utilisée par "
                + describe(usage) + ". Change leur catégorie ou supprime-les d'abord.");
    }

    private static String describe(CategoryRepository.CategoryUsage usage) {
        List<String> parts = new ArrayList<>();
        add(parts, usage.getTransactions(), "transaction", "transactions");
        add(parts, usage.getBudgets(), "budget", "budgets");
        add(parts, usage.getProvisions(), "provision", "provisions");
        add(parts, usage.getRecurrings(), "transaction récurrente", "transactions récurrentes");
        return parts.size() == 1
                ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " et " + parts.get(parts.size() - 1);
    }

    private static void add(List<String> parts, long count, String singular, String plural) {
        if (count > 0) {
            parts.add(count + " " + (count == 1 ? singular : plural));
        }
    }

    static boolean isUsed(CategoryRepository.CategoryUsage usage) {
        return usage.getTransactions() + usage.getBudgets() + usage.getProvisions() + usage.getRecurrings() > 0;
    }
}
