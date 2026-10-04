package com.bank.money_transfer.dto;

import com.bank.money_transfer.enumFile.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class TransferResponse {

    private final Long transferId;
    private final TransferStatus status;
    private final Long fromAccountId;
    private final Long toAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant createdAt;
}
