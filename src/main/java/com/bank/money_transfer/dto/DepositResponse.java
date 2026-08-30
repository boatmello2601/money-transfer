package com.bank.money_transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class DepositResponse {
    private final Long accountId;
    private final BigDecimal balance;
    private final Long ledgerEntryId;
}
