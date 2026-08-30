package com.bank.money_transfer.service;

import com.bank.money_transfer.dto.AccountResponse;
import com.bank.money_transfer.dto.BalanceResponse;
import com.bank.money_transfer.dto.CreateAccountRequest;
import com.bank.money_transfer.dto.DepositResponse;
import com.bank.money_transfer.entity.AccountEntity;
import com.bank.money_transfer.entity.LedgerEntry;
import com.bank.money_transfer.enumFile.AccountStatus;
import com.bank.money_transfer.enumFile.EntryType;
import com.bank.money_transfer.exception.AccountNotFoundException;
import com.bank.money_transfer.exception.BusinessRuleException;
import com.bank.money_transfer.lock.AccountLockService;
import com.bank.money_transfer.repository.AccountRepository;
import com.bank.money_transfer.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Service
public class AccountService {
    private static final Set<String> ALLOWED_CURRENCIES = Set.of("THB", "USD", "EUR", "JPY", "SGD");

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountLockService accountLockService;

    public AccountService(AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator, LedgerEntryRepository ledgerEntryRepository, AccountLockService accountLockService) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountLockService = accountLockService;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        validate(request);

        AccountEntity account = new AccountEntity();
        account.setAccountNumber(accountNumberGenerator.generate());
        account.setOwnerName(request.getOwnerName().trim());
        account.setCurrency(request.getCurrency());
        account.setBalance(request.getInitialBalance() == null ? BigDecimal.ZERO : request.getInitialBalance());
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());

        AccountEntity saved = accountRepository.save(account);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account " + id + " not found"));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account " + id + " not found"));
        return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency(), Instant.now());
    }

    private void validate(CreateAccountRequest request) {
        if (request.getOwnerName() == null || request.getOwnerName().isBlank()) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-owner-name",
                    "Invalid owner name",
                    422,
                    "ownerName ต้องไม่ว่าง"
            );
        }
        if (request.getCurrency() == null || !ALLOWED_CURRENCIES.contains(request.getCurrency())) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-currency",
                    "Invalid currency",
                    422,
                    "currency ต้องเป็นหนึ่งใน " + ALLOWED_CURRENCIES
            );
        }
        if (request.getInitialBalance() != null && request.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-initial-balance",
                    "Invalid initial balance",
                    422,
                    "initialBalance ต้องไม่ติดลบ"
            );
        }
    }

    @Transactional
    public DepositResponse deposit(Long accountId, BigDecimal amount) {
        validateAmount(amount);

        String lockToken = acquireLockOrThrow(accountId);
        try {
            AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account " + accountId + " not found"));

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new BusinessRuleException(
                        "https://errors.bank.local/account-not-active",
                        "Account not active", 422,
                        "Account " + accountId + " is not ACTIVE");
            }

            BigDecimal newBalance = account.getBalance().add(amount);
            account.setBalance(newBalance);
            account.setUpdatedAt(Instant.now());
            accountRepository.save(account);

            LedgerEntry entry = new LedgerEntry();
            entry.setAccountId(accountId);
            entry.setEntryType(EntryType.CREDIT);
            entry.setAmount(amount);
            entry.setBalanceAfter(newBalance);
            entry.setCreatedAt(Instant.now());
            LedgerEntry savedEntry = ledgerEntryRepository.save(entry);

            return new DepositResponse(accountId, newBalance, savedEntry.getId());
        } finally {
            accountLockService.releaseLock(accountId, lockToken);
        }
    }

    private AccountResponse toResponse(AccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }

    private String acquireLockOrThrow(Long accountId) {
        String token;
        try {
            token = accountLockService.acquireLock(accountId);
        } catch (Exception ex) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/lock-unavailable",
                    "Lock service unavailable", 409,
                    "ไม่สามารถทำรายการได้ในขณะนี้ กรุณาลองใหม่");
        }
        if (token == null) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/account-locked",
                    "Account is locked", 409,
                    "บัญชี " + accountId + " กำลังถูกทำรายการอื่นอยู่ กรุณาลองใหม่");
        }
        return token;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                    "https://errors.bank.local/invalid-amount",
                    "Invalid amount", 422, "amount ต้องมากกว่า 0");
        }
    }
}
