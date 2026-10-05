package com.bank.money_transfer.repository;

import com.bank.money_transfer.entity.OutboxEvent;
import com.bank.money_transfer.enumFile.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
