package com.personalcashflow.category;

import com.personalcashflow.common.ConflictException;

public class CategoryHasChildrenException extends ConflictException {

    public CategoryHasChildrenException(String name) {
        super("Impossible de supprimer « " + name + " » : elle contient des sous-catégories. "
                + "Supprime-les ou rattache-les à une autre catégorie d'abord.");
    }
}
