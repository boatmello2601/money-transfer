package com.bank.money_transfer.exception;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {
    private final String type;
    private final String title;
    private final int status;

    public BusinessRuleException(String type, String title, int status, String detail) {
        super(detail);
        this.type = type;
        this.title = title;
        this.status = status;
    }
}
