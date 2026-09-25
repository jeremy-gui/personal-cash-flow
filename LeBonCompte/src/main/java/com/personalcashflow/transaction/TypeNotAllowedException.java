package com.personalcashflow.transaction;

import com.personalcashflow.category.Category;
import com.personalcashflow.common.BadRequestException;
import java.util.stream.Collectors;

public class TypeNotAllowedException extends BadRequestException {

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
