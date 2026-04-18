
# ConfessionVerse – Backend

Spring Boot backend powering the ConfessionVerse platform, responsible for authentication, AI integration, billing workflows, real-time messaging, and persistent data storage.

The application is deployed as a **Docker container on AWS EC2** with automated CI/CD.

## 🌐 Live Environment

The backend is deployed in a live cloud environment and integrated with the frontend application.

Note:
This environment is actively under development and testing.


---

# 🚀 Tech Stack

* Java 17
* Spring Boot 3
* Amazon RDS (MySQL)
* Docker
* GitHub Actions (CI/CD)
* AWS EC2
* Amazon ECR
* AWS Systems Manager (SSM)
* Stripe API
* OpenAI API
* SMTP email services

Infrastructure provisioning is handled separately using **Terraform** in the infrastructure repository.

---

# 🏗 Deployment Architecture

The backend runs as a stateless container behind a reverse proxy.

```text id="backend-arch"
Internet
   │
   ▼
Nginx Reverse Proxy
   │
   ▼
Spring Boot Container
   │
   ▼
Amazon RDS (MySQL)
```

Key characteristics:

* Nginx is the only publicly exposed service
* Backend container communicates with the database through private networking
* Database is not publicly accessible

---

# 🔐 Security & Production Features

Security mechanisms implemented in the backend:

* JWT-based authentication and authorization
* Password hashing using BCrypt
* Rate limiting using Bucket4j
* Input validation using Bean Validation
* Centralized exception handling
* Environment-based secret management

Security design principles:

* no secrets committed to source control
* backend isolated behind reverse proxy
* database deployed in private subnets

---

# 🤖 AI Integration

The backend integrates with the **OpenAI API**.

Capabilities include:

* AI-assisted functionality
* request validation
* secure API key handling
* robust error handling for external services

All AI interactions are processed server-side.

---

# 💳 Billing & Subscription System

Stripe integration provides a full subscription-based billing system.

Features include:

* Stripe Checkout integration
* subscription plan management
* free trial support
* plan upgrade and downgrade workflows
* subscription cancellation handling
* add-on / boost purchase functionality

Webhook security features:

* Stripe webhook signature verification
* server-side event validation
* payment state synchronization with database

Billing events trigger automated email notifications.

---

# 🌍 Internationalization (i18n)

The backend supports multi-language functionality.

Features:

* locale-based message resolution
* backend-driven language handling
* message resource files for translations

---

# 🐳 Containerization

The backend is packaged as a Docker container.

Container characteristics:

* built using a Dockerfile
* stateless architecture
* restart policy `unless-stopped`
* environment-based configuration
* fully decoupled from the host system

---

🔄 CI/CD Pipeline

Backend deployment is fully automated using GitHub Actions.

Deployment pipeline:
```text id="backend-arch"

Developer Push
│
▼
GitHub Actions
│
▼
Docker Image Build
│
▼
Push to Amazon ECR
│
▼
SSH Deployment to EC2
│
▼
docker-compose up -d --build

This approach ensures reproducible deployments without manual intervention.
```
---

# 🗄 Database

The backend connects to **Amazon RDS (MySQL 8)**.

Characteristics:

* managed database service
* private networking
* decoupled from EC2 lifecycle
* persistent storage independent from containers

Example connection format:

```id="jdbc-example"
jdbc:mysql://<rds-endpoint>:3306/confessionverse
```

---

# 📊 Logging & Configuration

Production logging configuration includes:

* optimized logging levels
* SQL logging disabled in production
* structured application logs
* environment-based configuration profiles

---

# 📌 Architecture Characteristics

* layered architecture (Controller → Service → Repository)
* JWT-secured API
* rate limiting protection
* third-party API integrations
* Dockerized deployment
* automated CI/CD pipeline
* private managed database
* stateless container-based architecture
