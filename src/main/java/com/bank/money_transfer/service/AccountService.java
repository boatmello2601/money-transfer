package com.bank.money_transfer.service;

import com.bank.money_transfer.dto.AccountResponse;
import com.bank.money_transfer.dto.BalanceResponse;
import com.bank.money_transfer.dto.CreateAccountRequest;
import com.bank.money_transfer.entity.AccountEntity;
import com.bank.money_transfer.enumFile.AccountStatus;
import com.bank.money_transfer.exception.AccountNotFoundException;
import com.bank.money_transfer.exception.BusinessRuleException;
import com.bank.money_transfer.repository.AccountRepository;
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

    public AccountService(AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
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
        fdsafdsafdsafdsafdsafdsafdfdsa

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
}
