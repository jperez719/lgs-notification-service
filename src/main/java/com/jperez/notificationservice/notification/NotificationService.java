package com.jperez.notificationservice.notification;

import com.jperez.notificationservice.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void recordAndSend(Map<String, Object> payload) {
        UUID tenantId = UUID.fromString((String) payload.get("tenantId"));
        UUID customerId = UUID.fromString((String) payload.get("customerId"));
        UUID transactionId = UUID.fromString((String) payload.get("transactionId"));
        String type = (String) payload.get("type");
        Object amount = payload.get("amount");
        Object resultingBalance = payload.get("resultingBalance");

        String message = String.format(
                "%s of %s applied. New balance: %s.",
                type, amount, resultingBalance
        );

        // Placeholder for a real notification channel (email/SMS) — see
        // NotificationEventListener in store-core-service for the same
        // deliberate design: prove the pattern first, plug in a real
        // channel later.
        log.info("[NOTIFICATION] Tenant {} - Customer {}: {} (transactionId={})",
                tenantId, customerId, message, transactionId);

        Notification notification = new Notification(
                tenantId, customerId, transactionId, message, "SENT"
        );
        notificationRepository.save(notification);
    }
}
