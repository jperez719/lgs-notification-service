## Cross-service / integration lessons

Lessons that span both notification-service and store-core-service
(e.g., shared Docker network conflicts, RabbitMQ contract issues) are
tracked centrally in store-core-service's LESSONS-LEARNED.md, since
that repo is the source of truth for integration-level decisions and
issues across services.

See: https://github.com/jperez719/lgs-store-crm/blob/main/docs/LESSONS-LEARNED.md


### Kubernetes Secrets need variable names matching what each specific container expects, not a single shared naming convention
A single Secret (notification-db-secret) used DB_USERNAME/DB_PASSWORD
— the naming convention Spring Boot's application-docker.properties
expects via ${DB_USERNAME}/${DB_PASSWORD} placeholders. The Postgres
container, however, uses the official postgres Docker image's own
entrypoint script, which looks only for POSTGRES_USER/POSTGRES_PASSWORD
— it has no awareness of any application-level naming convention.
Reusing one Secret's keys across both containers meant Postgres never
received the variables it needed at all, failing to initialize with
"superuser password is not specified" despite the Secret genuinely
containing a real password under a different key name.

Lesson: when a Postgres container and an application container both
need the same underlying credential, they typically need it under two
different environment variable names, and therefore two separate
Secret definitions (or one Secret referenced with explicit key
remapping via `env: - name: POSTGRES_PASSWORD valueFrom: secretKeyRef:
...`, rather than a blanket envFrom). Match each container's actual
expected variable names — usually documented on the base image itself
(e.g. the official postgres image's Docker Hub page) — rather than
assuming a project-wide naming convention applies everywhere.