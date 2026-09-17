package com.personalcashflow.imports.format;

import com.personalcashflow.imports.InvalidOfxFileException;
import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.TransactionList;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.OFXParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Parses a bank's OFX 1.x (SGML) statement export via the ofx4j library, which understands the
 * unclosed-tag SGML shape natively (a standard XML parser would reject it as malformed).
 */
@Component
public class OfxBankFormat {

    /** One entry per OFX transaction record; empty for a record filtered out (e.g. zero amount). */
    public List<Optional<ImportedRow>> parse(InputStream content) {
        ResponseEnvelope envelope = unmarshal(content);
        List<Optional<ImportedRow>> rows = new ArrayList<>();
        for (BankStatementResponseTransaction statementResponse : bankingResponses(envelope)) {
            TransactionList transactionList = statementResponse.getMessage().getTransactionList();
            if (transactionList == null) {
                continue;
            }
            for (var transaction : transactionList.getTransactions()) {
                rows.add(toImportedRow(transaction));
            }
        }
        return rows;
    }

    private static ResponseEnvelope unmarshal(InputStream content) {
        try {
            return new AggregateUnmarshaller<>(ResponseEnvelope.class).unmarshal(content);
        } catch (OFXParseException e) {
            throw new InvalidOfxFileException(e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    private static List<BankStatementResponseTransaction> bankingResponses(ResponseEnvelope envelope) {
        ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.banking);
        if (!(messageSet instanceof BankingResponseMessageSet bankingResponseMessageSet)) {
            return List.of();
        }
        return bankingResponseMessageSet.getStatementResponses();
    }

    private static Optional<ImportedRow> toImportedRow(
            com.webcohesion.ofx4j.domain.data.common.Transaction transaction) {
        BigDecimal amount = transaction.getBigDecimalAmount();
        String label = transaction.getName() == null ? "" : transaction.getName().trim();
        String fitid = transaction.getId();
        if (amount == null
                || amount.signum() == 0
                || label.isEmpty()
                || fitid == null
                || fitid.isBlank()
                || transaction.getDatePosted() == null) {
            return Optional.empty();
        }

        LocalDate date = transaction.getDatePosted().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        String rawRecord = "FITID:%s DTPOSTED:%s TRNAMT:%s NAME:%s".formatted(
                fitid, date, amount.toPlainString(), label);
        return Optional.of(new ImportedRow(date, label, amount, fitid, rawRecord));
    }
}
