package com.bank.money_transfer.service;

import com.bank.money_transfer.dto.TransferRequest;
import com.bank.money_transfer.dto.TransferResponse;
import com.bank.money_transfer.entity.AccountEntity;
import com.bank.money_transfer.entity.LedgerEntry;
import com.bank.money_transfer.entity.Transfer;
import com.bank.money_transfer.enumFile.AccountStatus;
import com.bank.money_transfer.enumFile.EntryType;
import com.bank.money_transfer.enumFile.TransferStatus;
import com.bank.money_transfer.exception.AccountNotFoundException;
import com.bank.money_transfer.exception.BusinessRuleException;
import com.bank.money_transfer.helper.TransferOutcome;
import com.bank.money_transfer.lock.AccountLockService;
import com.bank.money_transfer.repository.AccountRepository;
import com.bank.money_transfer.repository.LedgerEntryRepository;
import com.bank.money_transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountLockService accountLockService;
    private final TransferFailureRecorder failureRecorder;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           AccountLockService accountLockService,
                           TransferFailureRecorder failureRecorder) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountLockService = accountLockService;
        this.failureRecorder = failureRecorder;
    }

    @Transactional
    public TransferOutcome transfer(TransferRequest request, String idempotencyKey) {

        String requestHash = computeHash(request);

        // ===== 1. เช็ค idempotency ก่อนอย่างอื่นทั้งหมด =====
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transfer t = existing.get();
            if (!t.getRequestHash().equals(requestHash)) {
                throw new BusinessRuleException(
                        "https://errors.bank.local/idempotency-conflict",
                        "Idempotency key conflict", 409,
                        "Idempotency-Key นี้เคยใช้กับ request ที่ต่างออกไป");
            }
            return new TransferOutcome(toResponse(t), false); // คืนผลลัพธ์เดิม ไม่ทำซ้ำ
        }

        // ===== 2. validate request-level (ไม่ต้องแตะ DB) =====
        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-request",
                    "Invalid request", 422, "fromAccountId และ toAccountId ต้องไม่ว่าง");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/self-transfer",
                    "Self transfer not allowed", 422, "ห้ามโอนเข้าบัญชีตัวเอง");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-amount",
                    "Invalid amount", 422, "amount ต้องมากกว่า 0");
        }

        // ===== 3. ล็อกเรียงตาม id น้อย → มาก เสมอ กัน deadlock =====
        Long firstId = Math.min(request.getFromAccountId(), request.getToAccountId());
        Long secondId = Math.max(request.getFromAccountId(), request.getToAccountId());

        String lockToken1 = acquireLockOrThrow(firstId);
        String lockToken2 = null;
        try {
            lockToken2 = acquireLockOrThrow(secondId);

            AccountEntity firstAccount = accountRepository.findByIdForUpdate(firstId)
                    .orElseThrow(() -> new AccountNotFoundException("Account " + firstId + " not found"));
            AccountEntity secondAccount = accountRepository.findByIdForUpdate(secondId)
                    .orElseThrow(() -> new AccountNotFoundException("Account " + secondId + " not found"));

            // กำหนดกลับว่าใครคือ from/to จริงตามที่ client ขอมา
            AccountEntity fromAccount = firstAccount.getId().equals(request.getFromAccountId()) ? firstAccount : secondAccount;
            AccountEntity toAccount = firstAccount.getId().equals(request.getFromAccountId()) ? secondAccount : firstAccount;

            // ===== 4. validate business rules (ยังไม่แก้ยอดอะไรเลย) =====
            try {
                validateBusinessRules(fromAccount, toAccount, request);
            } catch (BusinessRuleException ex) {
                failureRecorder.recordFailure(idempotencyKey, request, requestHash, ex.getMessage());
                throw ex;
            }

            // ===== 5. ผ่านหมดแล้ว ค่อยแก้ยอดจริง =====
            fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
            toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
            fromAccount.setUpdatedAt(Instant.now());
            toAccount.setUpdatedAt(Instant.now());
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            Transfer transfer = new Transfer();
            transfer.setIdempotencyKey(idempotencyKey);
            transfer.setFromAccountId(request.getFromAccountId());
            transfer.setToAccountId(request.getToAccountId());
            transfer.setAmount(request.getAmount());
            transfer.setCurrency(request.getCurrency());
            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setRequestHash(requestHash);
            transfer.setCreatedAt(Instant.now());
            Transfer savedTransfer = transferRepository.save(transfer);

            LedgerEntry debit = new LedgerEntry();
            debit.setAccountId(fromAccount.getId());
            debit.setTransferId(savedTransfer.getId());
            debit.setEntryType(EntryType.DEBIT);
            debit.setAmount(request.getAmount());
            debit.setBalanceAfter(fromAccount.getBalance());
            debit.setCreatedAt(Instant.now());
            ledgerEntryRepository.save(debit);

            LedgerEntry credit = new LedgerEntry();
            credit.setAccountId(toAccount.getId());
            credit.setTransferId(savedTransfer.getId());
            credit.setEntryType(EntryType.CREDIT);
            credit.setAmount(request.getAmount());
            credit.setBalanceAfter(toAccount.getBalance());
            credit.setCreatedAt(Instant.now());
            ledgerEntryRepository.save(credit);

            return new TransferOutcome(toResponse(savedTransfer), true);

        } finally {
            if (lockToken2 != null) {
                accountLockService.releaseLock(secondId, lockToken2);
            }
            accountLockService.releaseLock(firstId, lockToken1);
        }
    }

    private void validateBusinessRules(AccountEntity fromAccount, AccountEntity toAccount, TransferRequest request) {
        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/account-not-active",
                    "Account not active", 422, "บัญชีต้นทางหรือปลายทางไม่ใช่ ACTIVE");
        }
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())
                || !fromAccount.getCurrency().equals(request.getCurrency())) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/currency-mismatch",
                    "Currency mismatch", 422, "สกุลเงินของบัญชีต้นทาง ปลายทาง และ request ต้องตรงกัน");
        }
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/insufficient-funds",
                    "Insufficient funds", 422,
                    "Account " + fromAccount.getId() + " has balance " + fromAccount.getBalance()
                            + " but requested " + request.getAmount());
        }
    }

    private String acquireLockOrThrow(Long accountId) {
        String token;
        try {
            token = accountLockService.acquireLock(accountId);
        } catch (Exception ex) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/lock-unavailable",
                    "Lock service unavailable", 409, "ไม่สามารถทำรายการได้ในขณะนี้ กรุณาลองใหม่");
        }
        if (token == null) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/account-locked",
                    "Account is locked", 409, "บัญชี " + accountId + " กำลังถูกทำรายการอื่นอยู่ กรุณาลองใหม่");
        }
        return token;
    }

    private String computeHash(TransferRequest request) {
        try {
            String raw = request.getFromAccountId() + ":" + request.getToAccountId() + ":"
                    + request.getAmount().stripTrailingZeros().toPlainString() + ":" + request.getCurrency();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(t.getId(), t.getStatus(), t.getFromAccountId(), t.getToAccountId(),
                t.getAmount(), t.getCurrency(), t.getCreatedAt());
    }
}
