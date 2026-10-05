package com.bank.money_transfer.service;

import com.bank.money_transfer.dto.DepositResponse;
import com.bank.money_transfer.entity.AccountEntity;
import com.bank.money_transfer.enumFile.AccountStatus;
import com.bank.money_transfer.exception.BusinessRuleException;
import com.bank.money_transfer.lock.AccountLockService;
import com.bank.money_transfer.repository.AccountRepository;
import com.bank.money_transfer.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountLockService accountLockService;

    @InjectMocks
    private AccountService accountService;

    private AccountEntity activeAccount;

    @BeforeEach
    void setUp() {
        activeAccount = new AccountEntity();
        activeAccount.setId(1L);
        activeAccount.setAccountNumber("0000000001");
        activeAccount.setBalance(new BigDecimal("1000.0000"));
        activeAccount.setCurrency("THB");
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setCreatedAt(Instant.now());
        activeAccount.setUpdatedAt(Instant.now());
    }

    @Test
    void withdraw_shouldReduceBalance_whenFundsSufficient() {
        when(accountLockService.acquireLock(1L)).thenReturn("fake-lock-token");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DepositResponse response = accountService.withdraw(1L, new BigDecimal("300.00"));

        assertThat(response.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void withdraw_shouldThrow422_whenInsufficientFunds() {
        when(accountLockService.acquireLock(1L)).thenReturn("fake-lock-token");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.withdraw(1L, new BigDecimal("9999.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("has balance");
    }

    @Test
    void withdraw_shouldThrow409_whenAccountAlreadyLocked() {
        when(accountLockService.acquireLock(1L)).thenReturn(null); // จำลองว่า lock ไม่ว่าง

        assertThatThrownBy(() -> accountService.withdraw(1L, new BigDecimal("100.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("กำลังถูกทำรายการอื่นอยู่");
    }
}
