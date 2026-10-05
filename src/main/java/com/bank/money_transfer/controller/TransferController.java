package com.bank.money_transfer.controller;

import com.bank.money_transfer.dto.TransferRequest;
import com.bank.money_transfer.dto.TransferResponse;
import com.bank.money_transfer.exception.BusinessRuleException;
import com.bank.money_transfer.helper.TransferOutcome;
import com.bank.money_transfer.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TransferRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/missing-idempotency-key",
                    "Missing Idempotency-Key", 400, "ต้องส่ง header Idempotency-Key มาด้วย");
        }

        TransferOutcome outcome = transferService.transfer(request, idempotencyKey);
        TransferResponse response = outcome.getResponse();

        if (outcome.isCreated()) {
            URI location = URI.create("/api/v1/transfers/" + response.getTransferId());
            return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }
}
