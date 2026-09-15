package com.jperez.notificationservice.notification;

import com.jperez.notificationservice.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreditTransactionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CreditTransactionNotificationListener.class);

    private final NotificationService notificationService;

    public CreditTransactionNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void handleCreditTransactionApplied(Map<String, Object> payload) {
        log.info("Received credit-transaction.applied event: {}", payload);
        notificationService.recordAndSend(payload);
    }
}
