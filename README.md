Pulse Synapse

Overview
Pulse Synapse is a polyglot-friendly, microservices-based monitoring and analytics platform built with Java 21 and Spring Boot 3. It ingests metrics/events, stores time-series data, manages monitoring targets, schedules polling jobs, and exposes analytics/reporting endpoints. Services communicate over HTTP/REST, AMQP (RabbitMQ), and gRPC, with shared protobuf contracts.

Stack
- Language: Java 21
- Build tool / package manager: Maven (multi-module)
- Frameworks & libraries:
  - Spring Boot 3 (Web, Security, Data JPA, Validation, AMQP, WebFlux)
  - Spring gRPC (server/client) + gRPC services
  - Protobuf (compiled via protobuf-maven-plugin)
  - Persistence: PostgreSQL (JDBC), Hibernate JPA
  - Messaging: RabbitMQ (AMQP)
  - Time-series: InfluxDB (in analytics & ingestion services)
  - Auth: JSON Web Tokens (jjwt)
- Modules/services:
  - proto-module: Shared protobuf/gRPC definitions
  - user-auth-service: Authentication & JWT issuance/validation (HTTP + gRPC)
  - target-management-service: CRUD for monitoring targets (HTTP + gRPC)
  - scheduler-service: Schedules polling jobs; communicates via RabbitMQ and gRPC
  - polling-worker-service: Consumes jobs and performs network polling (AMQP + HTTP/WebFlux)
  - data-ingestion-service: Receives data and writes to InfluxDB
  - analytics-reporting-service: Reads from InfluxDB and serves analytics

Monorepo Layout
- pom.xml — Parent POM (Spring Boot parent 3.5.5, Java 21)
- proto-module/
- user-auth-service/
- target-management-service/
- scheduler-service/
- polling-worker-service/
- data-ingestion-service/
- analytics-reporting-service/

Entry Points (main classes)
- user-auth-service: pr.pulsesynapse.PulseSynapseApplication
- Other services: Spring Boot standard structure (main class under each module’s src/main/java). If a service’s main class differs, update this section. TODO: Verify and list main classes for all services explicitly.

Default Ports & External Dependencies
- user-auth-service: ${AUTH_SERVICE_PORT:8080}
- target-management-service: ${TARGET_SERVICE_PORT:8081}
- scheduler-service: 8082 (RabbitMQ required)
- polling-worker-service: 8083 (RabbitMQ required)
- data-ingestion-service: 8084 (InfluxDB required)
- analytics-reporting-service: 8085 (InfluxDB required)
- PostgreSQL: required by user-auth-service and target-management-service
- RabbitMQ: required by scheduler-service and polling-worker-service
- InfluxDB: required by data-ingestion-service and analytics-reporting-service

Environment Variables
Repository root .env (used by services via spring-dotenv):
- DB_URL — JDBC URL (PostgreSQL)
- DB_USERNAME — DB user
- DB_PASSWORD — DB password
- JWT_SECRET — HMAC secret for JWT
- JWT_EXPIRATION — JWT expiration in ms
- AUTH_SERVICE_PORT — default: 8080
- TARGET_SERVICE_PORT — default: 8081
- AUTH_SCHEMA — default: auth_management
- TARGET_SCHEMA — default: target_management

Per-service application.properties (key highlights)
- user-auth-service
  - server.port=${AUTH_SERVICE_PORT:8080}
  - spring.datasource.url=${DB_URL}
  - spring.jpa.properties.hibernate.default_schema=${AUTH_SCHEMA:auth_management}
  - jwt.secret=${JWT_SECRET}, jwt.expiration=${JWT_EXPIRATION}
- target-management-service
  - server.port=${TARGET_SERVICE_PORT:8081}
  - spring.datasource.url=${DB_URL}&currentSchema=${TARGET_SCHEMA:target_management}
  - spring.jpa.properties.hibernate.default_schema=${TARGET_SCHEMA:target_management}
  - jwt.secret=${JWT_SECRET}, jwt.expiration=${JWT_EXPIRATION}
- scheduler-service
  - server.port=8082
  - spring.rabbitmq.host/port/username/password (defaults: localhost:5672 guest/guest)
  - spring.grpc.client.channels.target-management-service.address=static://localhost:8081 (plaintext)
  - polling.schedule.rate.ms=60000
- polling-worker-service
  - server.port=8083
  - spring.rabbitmq.* (defaults: localhost:5672 guest/guest)
- data-ingestion-service
  - server.port=8084
  - influxdb.url, influxdb.token, influxdb.org, influxdb.bucket
- analytics-reporting-service
  - server.port=8085
  - influxdb.url, influxdb.token, influxdb.org, influxdb.bucket

Requirements
- Java 21 (JDK)
- Maven 3.9+
- PostgreSQL database (for auth and target services)
- RabbitMQ broker (for scheduler and polling worker)
- InfluxDB 2.x (for ingestion and analytics)

Build
- Build everything from the repo root:
  - mvn clean package
- Generate protobuf/gRPC stubs (runs as part of the build):
  - mvn -pl proto-module -am clean package

Run (local, without Docker)
- Prepare environment variables (.env in repo root is supported by spring-dotenv) or set via OS env.
- Start dependencies:
  - PostgreSQL (ensure schemas AUTH_SCHEMA and TARGET_SCHEMA exist if not created automatically)
  - RabbitMQ
  - InfluxDB
- Start individual services from their module directories:
  - User Auth: mvn spring-boot:run -pl user-auth-service -am
  - Target Management: mvn spring-boot:run -pl target-management-service -am
  - Scheduler: mvn spring-boot:run -pl scheduler-service -am
  - Polling Worker: mvn spring-boot:run -pl polling-worker-service -am
  - Data Ingestion: mvn spring-boot:run -pl data-ingestion-service -am
  - Analytics Reporting: mvn spring-boot:run -pl analytics-reporting-service -am

Testing
- From the repo root: mvn test
- Or per module, e.g.: mvn -pl user-auth-service test

How to run this app in deployment
Option A — Docker (build JARs first, then build minimal runtime images)
1) Build all modules:
   - mvn clean package
2) Build per-service images (from repo root):
   - docker build -t pulsesynapse/user-auth-service:latest user-auth-service
   - docker build -t pulsesynapse/target-management-service:latest target-management-service
   - docker build -t pulsesynapse/scheduler-service:latest scheduler-service
   - docker build -t pulsesynapse/polling-worker-service:latest polling-worker-service
   - docker build -t pulsesynapse/data-ingestion-service:latest data-ingestion-service
   - docker build -t pulsesynapse/analytics-reporting-service:latest analytics-reporting-service
3) Run containers (sample, adjust env/links as needed):
   - PostgreSQL:
     docker run -d --name ps-postgres -e POSTGRES_DB=neondb -e POSTGRES_USER=${DB_USERNAME} -e POSTGRES_PASSWORD=${DB_PASSWORD} -p 5432:5432 postgres:16
   - RabbitMQ:
     docker run -d --name ps-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:3-management
   - InfluxDB:
     docker run -d --name ps-influx -p 8086:8086 influxdb:2
   - Services (examples):
     docker run -d --name user-auth --env-file .env --network host pulsesynapse/user-auth-service:latest
     docker run -d --name target-mgmt --env-file .env --network host pulsesynapse/target-management-service:latest
     docker run -d --name scheduler --env-file .env --network host -e SPRING_RABBITMQ_HOST=host.docker.internal pulsesynapse/scheduler-service:latest
     docker run -d --name polling --env-file .env --network host -e SPRING_RABBITMQ_HOST=host.docker.internal pulsesynapse/polling-worker-service:latest
     docker run -d --name ingestion --env-file .env --network host -e INFLUXDB_URL=http://host.docker.internal:8086 pulsesynapse/data-ingestion-service:latest
     docker run -d --name analytics --env-file .env --network host -e INFLUXDB_URL=http://host.docker.internal:8086 pulsesynapse/analytics-reporting-service:latest

Notes:
- On Linux, replace host.docker.internal with the host IP or proper Docker network aliases.
- For production, create proper Docker networks and do not use --network host.
- Configure secure secrets injection (not plain .env), TLS, and health checks.

Option B — Multi-stage Docker build per service (no prebuilt JARs)
- Each service directory includes a Dockerfile that can optionally be adapted to build from source in a multi-stage build. For faster iterative builds in CI, you may prefer building JARs first (Option A).

Option C — Docker Compose or Kubernetes
- A docker-compose.yml (not provided) can orchestrate Postgres, RabbitMQ, InfluxDB, and all services with correct environment wiring. TODO: Add a compose file with sane defaults.
- Kubernetes manifests/Helm charts are not yet included. TODO: Provide k8s deployment YAMLs.

Scripts & Useful Maven Commands
- Package all: mvn clean package
- Run one service with dependencies built: mvn spring-boot:run -pl <module> -am
- Test one module: mvn -pl <module> test

Project Structure (condensed)
- proto-module: shared .proto files and generated sources
- user-auth-service: Spring Security + JWT; PostgreSQL; gRPC client/server
- target-management-service: CRUD + PostgreSQL; gRPC client/server
- scheduler-service: Schedulers + RabbitMQ + gRPC client
- polling-worker-service: Job consumer; RabbitMQ; WebFlux
- data-ingestion-service: InfluxDB writer
- analytics-reporting-service: InfluxDB reader/API

License
- TODO: Add license information (e.g., MIT/Apache-2.0). This repository does not currently contain a LICENSE file.

Security & Secrets
- Do NOT commit real secrets. The provided .env values should be treated as examples only. Use a secrets manager for production.

Known Gaps / TODOs
- Confirm and list the exact main classes for all services.
- Add docker-compose.yml with service-to-service networking and seeded dependencies.
- Add health/readiness endpoints and document them.
- Add LICENSE file.
- Verify user-auth-service application.properties trailing backslash and fix if necessary.
