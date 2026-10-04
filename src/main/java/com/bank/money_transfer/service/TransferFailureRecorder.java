package com.bank.money_transfer.service;

import com.bank.money_transfer.dto.TransferRequest;
import com.bank.money_transfer.entity.Transfer;
import com.bank.money_transfer.enumFile.TransferStatus;
import com.bank.money_transfer.repository.TransferRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class TransferFailureRecorder {

    private final TransferRepository transferRepository;

    public TransferFailureRecorder(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String idempotencyKey, TransferRequest request, String requestHash, String reason) {
        Transfer transfer = new Transfer();
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setFromAccountId(request.getFromAccountId());
        transfer.setToAccountId(request.getToAccountId());
        transfer.setAmount(request.getAmount());
        transfer.setCurrency(request.getCurrency());
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setRequestHash(requestHash);
        transfer.setFailureReason(reason);
        transfer.setCreatedAt(Instant.now());
        transferRepository.save(transfer);
    }
}
