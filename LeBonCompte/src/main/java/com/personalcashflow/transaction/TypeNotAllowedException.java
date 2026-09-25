package com.personalcashflow.transaction;

import com.personalcashflow.category.Category;
import com.personalcashflow.common.BadRequestException;
import java.util.stream.Collectors;

public class TypeNotAllowedException extends BadRequestException {

    /**
     * A transaction (or recurring transaction) entered by hand must use one of its category's
     * allowed types: the root's list, inherited by sub-categories; empty = any type.
     */
    public static void check(TransactionType type, Category category) {
        Category root = category;
        while (root != null && root.getParent() != null) {
            root = root.getParent();
        }
        if (root != null && !root.getAllowedTypes().isEmpty() && !root.getAllowedTypes().contains(type)) {
            throw new TypeNotAllowedException(type, root);
        }
    }

    public TypeNotAllowedException(TransactionType type, Category root) {
        super("Le type " + label(type) + " n'est pas autorisé dans « " + root.getName() + " » (types possibles : "
                + root.getAllowedTypes().stream().map(TypeNotAllowedException::label).collect(Collectors.joining(", "))
                + ").");
    }

    private static String label(TransactionType type) {
        return switch (type) {
            case DEPENSE -> "Dépense";
            case REVENU -> "Revenu";
            case TRANSFERT -> "Transfert";
        };
    }
}
