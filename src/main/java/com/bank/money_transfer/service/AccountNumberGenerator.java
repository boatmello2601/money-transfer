package com.bank.money_transfer.service;

import com.bank.money_transfer.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {
    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {
        String candidate;
        int attempts = 0;
        do {
            candidate = String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Unable to generate unique account number");
            }
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
