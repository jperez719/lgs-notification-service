# LGS Notification Service

A small Spring Boot service that listens for **credit transaction events** published by [`lgs-store-crm`](https://github.com/jperez719/lgs-store-crm) over RabbitMQ and records a notification for each one in its own PostgreSQL database.

It exists mainly as the second half of a learning project on microservices: the core service does the transactional work, and this one reacts to it asynchronously without being coupled to it.

## What it does

- Consumes `credit-transaction.applied` messages from the `store.events` topic exchange (queue `notification.credit-transaction.applied`)
- Persists a `notifications` row per event in its own database (`notification_db`)
- Runs on port **8081**
- Shares no code or database with the core service. The only contract is the JSON message.

## Tech stack

Java 25, Spring Boot 4.1, Maven, Spring Data JPA, PostgreSQL 16, Flyway, Spring AMQP (RabbitMQ) with `JacksonJsonMessageConverter`, Docker / Docker Compose, Kubernetes.

## What this project was intended to teach

| Area | What I practiced |
|---|---|
| Consuming messages | `@RabbitListener`, durable queues, bindings, and JSON deserialization |
| Message contracts | Agreeing on a JSON shape instead of sharing Java classes (and why Java serialization is a trap) |
| Database per service | A separate Postgres and schema with no foreign keys back to the core DB |
| Loose coupling | The core service never calls this one; it can be down and messages wait in the queue |
| Docker networking | Two Compose projects on an external shared network; service names must be globally unique |
| Kubernetes for a second service | Secrets, ConfigMaps, PVC-backed Postgres, ClusterIP Service, and init containers that wait for Postgres and RabbitMQ |
| Debugging distributed systems | Following a message across services using logs, the RabbitMQ UI and database queries |

## Run locally

```bash
# Shared network (create once, shared with lgs-store-crm)
docker network create store-crm-shared

# Create a .env with DB and RabbitMQ credentials (not committed), and export
# the same variables in your shell (shell variables override .env)

docker compose up -d --build
```

- Postgres is published on host port **5433** (the core service uses 5432)
- RabbitMQ is expected from the core service's compose project (host `rabbitmq` on the shared network)
- Check results: `select * from notifications order by created_at desc;`

Kubernetes manifests are in `k8s/`. Real `*secret*.yaml` files are gitignored; copy the `*.example.yaml` versions and fill in values.

## Related

Architecture decisions and cross-service lessons live in the core repo: https://github.com/jperez719/lgs-store-crm (`docs/decisions/` and `docs/LESSONS-LEARNED.md`).
