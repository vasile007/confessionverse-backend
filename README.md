ConfessionVerse Backend

Production-ready Spring Boot backend deployed in Docker on AWS EC2, using Amazon RDS for managed database infrastructure.

This service powers the ConfessionVerse platform, handling authentication, AI integration, billing, real-time messaging, and persistent data storage.

🚀 Tech Stack

Java 21

Spring Boot 3

Amazon RDS (MySQL 8)

Docker

AWS EC2

Stripe (Billing)

OpenAI API

SMTP (Email Service)

🏗 Production Architecture

Single EC2 instance with managed database architecture:

Internet
   ↓
Nginx (Docker)
   ↓
Frontend (Docker)
   ↓
Backend (Docker - Spring Boot)
   ↓
Amazon RDS (MySQL 8 - Managed)
Infrastructure Characteristics

Backend runs inside Docker on EC2

Database runs on Amazon RDS (managed service)

RDS is deployed inside the same VPC

Access to database restricted via Security Groups

Database is not publicly accessible

Encryption enabled (AWS KMS)

Automated backups enabled (RDS managed)

Secrets are injected via environment variables and never committed to source control.

🐳 Docker Deployment
Build Image
docker build -t confessionverse-backend .
Run Container
docker run -d \
  --name confessionverse-backend \
  --network confessionverse-network \
  --env-file .env \
  --restart unless-stopped \
  -p 8082:8082 \
  confessionverse-backend
Container Characteristics

Automatic restart on failure

Automatic restart after server reboot

Environment-based secret injection

Fully stateless application container

No systemd services.
No manual java -jar execution.

🔐 Environment Variables

Production secrets are stored only in a server-side .env file.

Example .env.example:

spring.datasource.url=
spring.datasource.username=
spring.datasource.password=

JWT_SECRET=
OPENAI_API_KEY=

STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SIGNING_SECRET=

SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASS=
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
SMTP_FROM=

⚠ .env is never committed to Git.

🗄 Database (Production)

Production database runs on Amazon RDS (MySQL 8).

Characteristics

Managed by AWS

Automated backups enabled

Encryption at rest (KMS)

Not publicly accessible

Accessible only from EC2 Security Group

Network-restricted via VPC

Connection Example
jdbc:mysql://<rds-endpoint>:3306/confessionverse

Database is fully decoupled from the EC2 instance.

📊 Logging Configuration

Production logging configuration:

spring.jpa.show-sql=false
logging.level.root=INFO

SQL logging disabled

Debug logging disabled

Optimized for performance and minimal log noise

🔄 Rebuild & Redeploy
./mvnw clean package -DskipTests

docker stop confessionverse-backend
docker rm confessionverse-backend

docker build -t confessionverse-backend .
docker run ...
🛡 Security Considerations

Secrets managed via environment variables

No hardcoded credentials

RDS not publicly exposed

Database access restricted via Security Groups

Encryption enabled at rest

Automatic restart policy enabled

Minimal attack surface (only Nginx publicly exposed)

🔮 Planned Improvements

CI/CD pipeline (GitHub Actions)

Docker image publishing to AWS ECR

Infrastructure as Code (Terraform)

HTTPS via Let's Encrypt

Horizontal scaling (Load Balancer + multiple EC2 instances)

Migration to container orchestration (ECS / EKS)

📌 Current Status

Production-ready single-instance architecture with managed database.

Designed for MVP scalability and clean separation between:

Application layer (Docker)

Reverse proxy layer (Nginx)

Managed database layer (Amazon RDS)
