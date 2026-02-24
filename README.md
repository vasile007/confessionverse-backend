ConfessionVerse Backend

Production-ready Spring Boot backend deployed in Docker on AWS EC2.

This backend powers the ConfessionVerse platform, handling authentication, AI integration, billing, real-time features, and database operations.

🚀 Tech Stack

Java 21

Spring Boot 3

MySQL 8

Docker

AWS EC2

Stripe (Billing)

OpenAI API

SMTP (Email Service)

🏗 Production Architecture (Current Setup)

Single EC2 instance architecture:

EC2
├── Docker
│ └── confessionverse-backend (container)
├── MySQL (local instance)
└── Automated daily database backups (cron)

The backend runs inside Docker with environment-based configuration.
Secrets are not stored in source control.

🐳 Docker Setup
Build Image
docker build -t confessionverse-backend .
Run Container
docker run -d \
  --name confessionverse-backend \
  --network host \
  --env-file .env \
  --restart unless-stopped \
  confessionverse-backend

Container is configured with:

Automatic restart on failure

Automatic restart after server reboot

Environment-based secret injection

🔐 Environment Variables

Production secrets are stored only in a server-side .env file.

Example .env.example:

DB_PASSWORD=
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

🗄 Database

MySQL runs locally on the EC2 instance.

Spring Boot connection string:

jdbc:mysql://localhost:3306/confessionverse

Database access is not publicly exposed.

💾 Automated Backups

Daily MySQL backup via cron at 03:00 AM:

mysqldump -u root -pPASSWORD confessionverse | gzip > /home/ubuntu/backups/backup_$(date +%F).sql.gz

Backups stored in:

/home/ubuntu/backups

Backup strategy ensures data recoverability in case of server failure.

📊 Logging

Production logging configuration:

spring.jpa.show-sql=false
logging.level.root=INFO

SQL logging disabled

Debug logs disabled in production

Minimal logging footprint for performance

🔄 Rebuild After Code Changes
./mvnw clean package -DskipTests

docker stop confessionverse-backend
docker rm confessionverse-backend

docker build -t confessionverse-backend .
docker run ...

Backend is fully containerized and no longer relies on:

systemd services

manual java -jar execution

🛡 Security & Production Notes

Secrets managed via environment variables

No hardcoded credentials

Docker restart policy enabled

Database not publicly exposed

Debug logging disabled

Production-ready single-instance deployment

🔮 Planned Improvements

Migration to AWS RDS

Nginx reverse proxy

HTTPS via Let's Encrypt

CI/CD pipeline

AWS ECR

Infrastructure as Code (Terraform)

📌 Status

Production-ready for single-instance deployment.
Currently optimized for MVP and controlled scaling.
