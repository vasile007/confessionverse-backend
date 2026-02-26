ConfessionVerse Backend

Production-grade Spring Boot backend deployed on AWS using Infrastructure as Code and a fully automated CI/CD pipeline.

This service powers the ConfessionVerse platform, handling authentication, AI integration, billing, real-time messaging, and persistent data storage.

🚀 Tech Stack

Java 21

Spring Boot 3

Amazon RDS (MySQL 8)

Docker

AWS EC2

AWS ECR

AWS SSM

Terraform (Infrastructure as Code)

GitHub Actions (CI/CD)

Stripe (Billing)

OpenAI API

SMTP (Email Service)

🏗 Production Architecture

Single-instance cloud architecture provisioned via Terraform:

Internet
↓
Nginx (Docker container)
↓
Frontend (Docker container)
↓
Backend (Docker – Spring Boot)
↓
Amazon RDS (MySQL 8 – Managed)

Infrastructure Characteristics

Entire infrastructure provisioned using Terraform

EC2 instance deployed inside custom VPC

RDS deployed in private subnets

Security Groups restrict database access to EC2 only

RDS not publicly accessible

Encryption at rest enabled (AWS KMS)

Automated backups managed by RDS

Remote Terraform state with locking (S3 + DynamoDB)

🐳 Containerization

Backend runs as a stateless Docker container:

Built via Dockerfile

No systemd services

No manual java -jar

Automatic restart policy

Environment-based configuration

Container properties:

--restart unless-stopped

Environment secrets injected via .env

Fully decoupled from host system

🔄 CI/CD Pipeline

Fully automated deployment pipeline using GitHub Actions.

On every push to main:

Docker image is built

Image is pushed to AWS ECR

EC2 instance is accessed via AWS SSM

Existing container is stopped and removed

New image is pulled

Updated container is deployed automatically

Zero manual SSH.
Zero manual Docker commands.
Zero production drift.

Deployment is fully reproducible and automated.

🗄 Database Architecture

Production database runs on Amazon RDS (MySQL 8).

Characteristics:

Managed by AWS

Deployed inside private subnets

Not publicly accessible

Access restricted via Security Groups

Encryption at rest enabled (KMS)

Automated backups enabled

Database fully decoupled from EC2 lifecycle

Connection example:

jdbc:mysql://<rds-endpoint>:3306/confessionverse

🔐 Security Model

Secrets injected via environment variables

.env file stored only on EC2

No secrets committed to Git

IAM role attached to EC2 for SSM access

Private database networking

Minimal attack surface (only Nginx publicly exposed)

CI/CD uses IAM user with scoped permissions

📊 Logging & Configuration

Production logging configuration:

SQL logging disabled

Debug logging disabled

Optimized for performance

Application-level logs only

🧱 Infrastructure as Code

All infrastructure is defined using Terraform modules:

VPC

Subnets (public & private)

Route tables

Internet Gateway

Security Groups

EC2 instance

RDS instance

IAM roles

Remote state backend (S3 + DynamoDB lock)

Infrastructure is version-controlled and reproducible.

📌 Current Architecture Status

✔ Infrastructure as Code
✔ Private managed database
✔ Fully containerized backend
✔ CI/CD pipeline
✔ Automated Docker image publishing (ECR)
✔ Automated deployment via SSM
✔ No manual production operations

This architecture represents a production-ready single-instance cloud deployment designed for scalability evolution toward:

Application Load Balancer

Multi-instance EC2

ECS / EKS migration

Blue/Green deployments

Horizontal scaling

