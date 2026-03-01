
ConfessionVerse Backend

Production-grade Spring Boot backend deployed on AWS using Docker and a fully automated CI/CD pipeline.

This service powers the ConfessionVerse platform, handling authentication, AI integration, billing, real-time messaging, and persistent data storage.

🚀 Tech Stack

Java 17
Spring Boot 3
MySQL
Docker
GitHub Actions (CI/CD)
AWS EC2
AWS ECR
AWS SSM
Stripe (Billing)
OpenAI API
SMTP (Email Service)

Infrastructure is provisioned separately using Terraform (see infrastructure repository).

🏗 Application Deployment Architecture

Single-instance Docker-based deployment:

Internet
↓
Nginx (reverse proxy container)
↓
Spring Boot Backend (Docker container)
↓
Amazon RDS (MySQL – private networking)

The backend is deployed as a stateless container and connects securely to a managed MySQL database.

🐳 Containerization

Backend runs as a stateless Docker container:

Built using Dockerfile

No systemd services

No manual java -jar

Automatic restart policy (--restart unless-stopped)

Environment-based configuration

Secrets injected via environment variables

Fully decoupled from host system

🔄 CI/CD Pipeline

Fully automated deployment pipeline using GitHub Actions.

On every push to main:

Docker image is built

Image is pushed to Amazon ECR

EC2 instance is accessed via AWS SSM

Existing container is stopped and removed

New image is pulled

Updated container is deployed automatically

No manual SSH. No manual Docker commands. No production drift.

Deployment is fully reproducible and automated.

🗄 Database

Production database runs on Amazon RDS (MySQL 8).

Managed MySQL service

Securely connected from backend container

Database lifecycle decoupled from EC2 instance

Connection example:

jdbc:mysql://<host>:3306/confessionverse
🔐 Security Model (Application Level)

Secrets injected via environment variables

.env file stored only on EC2

No secrets committed to Git

IAM role used for secure container deployment

Minimal public attack surface (only Nginx exposed)

📊 Logging & Configuration

Production logging configuration:

SQL logging disabled

Debug logging disabled

Optimized for performance

Application-level logs only

📌 Architecture Characteristics

✔ Fully containerized backend
✔ Automated CI/CD deployment
✔ Secure database connectivity
✔ Reproducible Docker-based deployment
✔ Infrastructure provisioned separately via Terraform
