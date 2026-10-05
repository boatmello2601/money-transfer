package com.bank.money_transfer.messaging;

import com.bank.money_transfer.entity.OutboxEvent;
import com.bank.money_transfer.enumFile.OutboxStatus;
import com.bank.money_transfer.repository.OutboxEventRepository;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String QUEUE_NAME = "TRANSFER.COMPLETED";

    private final OutboxEventRepository outboxEventRepository;
    private final JmsTemplate jmsTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, JmsTemplate jmsTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.jmsTemplate = jmsTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                jmsTemplate.convertAndSend(QUEUE_NAME, event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event id={}", event.getId());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), ex.getMessage(), ex);
            }
        }
    }
}
