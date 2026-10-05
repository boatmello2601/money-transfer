package com.bank.money_transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class TransferCompletedEvent {
    private final String eventId;
    private final String eventType;
    private final Long transferId;
    private final Long fromAccountId;
    private final Long toAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant occurredAt;
}
