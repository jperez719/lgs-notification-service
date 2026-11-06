package com.jperez.notificationservice.notification;

import com.jperez.notificationservice.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class NotificationServiceIdempotencyTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static RabbitMQContainer rabbitMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CreditTransactionNotificationListener listener;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanTable() {
        notificationRepository.deleteAll();
    }

    private Map<String, Object> payload(UUID transactionId) {
        return Map.of(
                "tenantId", UUID.randomUUID().toString(),
                "customerId", UUID.randomUUID().toString(),
                "transactionId", transactionId.toString(),
                "type", "CREDIT",
                "amount", new BigDecimal("50.00"),
                "resultingBalance", new BigDecimal("50.00")
        );
    }

    @Test
    void recordAndSend_calledTwiceWithSamePayload_createsOneNotification() {
        Map<String, Object> payload = payload(UUID.randomUUID());

        notificationService.recordAndSend(payload);
        notificationService.recordAndSend(payload);

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void listener_calledTwiceWithSamePayload_doesNotThrow() {
        Map<String, Object> payload = payload(UUID.randomUUID());

        assertThatCode(() -> {
            listener.handleCreditTransactionApplied(payload);
            listener.handleCreditTransactionApplied(payload);
        }).doesNotThrowAnyException();

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void database_rejectsSecondRowForSameTransactionId() {
        // Proves the constraint itself, independent of the exists() fast path.
        UUID transactionId = UUID.randomUUID();
        notificationRepository.saveAndFlush(new Notification(
                UUID.randomUUID(), UUID.randomUUID(), transactionId, "first", "SENT"));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(new Notification(
                UUID.randomUUID(), UUID.randomUUID(), transactionId, "second", "SENT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listener_calledConcurrentlyWithSamePayload_createsOneNotificationAndNoFailures()
            throws InterruptedException {
        Map<String, Object> payload = payload(UUID.randomUUID());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        Runnable task = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                listener.handleCreditTransactionApplied(payload);
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(task);
        executor.submit(task);

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(failures).isEmpty();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }
}
