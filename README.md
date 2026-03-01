ConfessionVerse – Backend

Spring Boot backend powering the ConfessionVerse platform, responsible for authentication, AI integration, billing workflows, real-time messaging, and persistent data storage.

Deployed as a Docker container on AWS with automated CI/CD.

🚀 Tech Stack

Java 17

Spring Boot 3

MySQL (Amazon RDS)

Docker

GitHub Actions (CI/CD)

AWS EC2, ECR, SSM

Stripe API

OpenAI API

SMTP

Infrastructure provisioning is handled separately via Terraform (see infrastructure repository).

🏗 Deployment Architecture

Single-instance Docker-based deployment:

Internet
→ Nginx (reverse proxy)
→ Spring Boot container
→ Amazon RDS (private networking)

The application runs as a stateless container and connects securely to a managed MySQL database.

🔐 Security & Production Features

JWT-based authentication and authorization

Password hashing using BCrypt

Rate limiting implemented using Bucket4j

Input validation using Bean Validation

Centralized exception handling

Environment-based secret management

No secrets committed to source control

Only Nginx is publicly exposed; backend and database operate within controlled networking.

🤖 AI Integration

Integration with OpenAI API

Backend-controlled API key management

Request validation and error handling

Secure third-party service communication

🐳 Containerization

Built via Dockerfile

Stateless architecture

Restart policy: --restart unless-stopped

Environment-based configuration

Fully decoupled from host system

🔄 CI/CD Pipeline

Automated deployment workflow:

Docker image build on push to main

Push image to Amazon ECR

Remote deployment via AWS Systems Manager

Container replacement without manual SSH

Deployment is reproducible and fully automated.

🗄 Database

Amazon RDS (MySQL 8):

Managed database service

Private networking configuration

Decoupled from EC2 lifecycle

Example connection format:

jdbc:mysql://<host>:3306/confessionverse
📊 Logging & Configuration

Production-optimized logging

SQL and debug logging disabled in production

Structured application-level logs

Environment-based profiles

📌 Architecture Characteristics

✔ Layered architecture (Controller → Service → Repository)
✔ JWT-secured API
✔ Rate limiting protection
✔ Third-party API integration
✔ Dockerized deployment
✔ Automated CI/CD
✔ Private managed database
