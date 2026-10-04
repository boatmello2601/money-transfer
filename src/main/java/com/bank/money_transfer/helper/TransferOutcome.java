package com.bank.money_transfer.helper;

import com.bank.money_transfer.dto.TransferResponse;

public class TransferOutcome {
    private final TransferResponse response;
    private final boolean created;

    public TransferOutcome(TransferResponse response, boolean created) {
        this.response = response;
        this.created = created;
    }

    public TransferResponse getResponse() { return response; }
    public boolean isCreated() { return created; }
}
