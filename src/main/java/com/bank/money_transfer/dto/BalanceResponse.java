package com.bank.money_transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class BalanceResponse {
    private final Long accountId;
    private final BigDecimal balance;
    private final String currency;
    private final Instant asOf;
}
