package com.bank.money_transfer.dto;

import com.bank.money_transfer.enumFile.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class AccountResponse {
    private final Long id;
    private final String accountNumber;
    private final String ownerName;
    private final String currency;
    private final BigDecimal balance;
    private final AccountStatus status;
    private final Instant createdAt;
}
