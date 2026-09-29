package com.personalcashflow.recurring;

import com.personalcashflow.common.BadRequestException;

public class MissingTransferAccountException extends BadRequestException {

    public MissingTransferAccountException() {
        super("Une récurrente de type Transfert a besoin d'un compte cible "
                + "(ou d'une catégorie qui en a un par défaut).");
    }
}
