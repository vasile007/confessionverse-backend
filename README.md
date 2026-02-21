ConfessionVerse Backend

Production-ready Spring Boot backend running in Docker on AWS EC2.

🚀 Tech Stack

Java 21

Spring Boot 3

MySQL 8

Docker

AWS EC2

Stripe (billing)

OpenAI API

SMTP (email)

📦 Production Architecture (Current Setup)

Single EC2 instance:

EC2
 ├── Docker
 │    └── confessionverse-backend (container)
 ├── MySQL (local)
 └── Daily automated database backups (cron)

Backend runs inside Docker with environment-based configuration.

🐳 Docker Setup
Build image
docker build -t confessionverse-backend .
Run container
docker run -d \
  --name confessionverse-backend \
  --network host \
  --env-file .env \
  --restart unless-stopped \
  confessionverse-backend

Container restarts automatically after server reboot.

🔐 Environment Variables

Production secrets are stored in a .env file on the server.

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

MySQL runs locally on EC2

Spring connects via:

jdbc:mysql://localhost:3306/confessionverse
💾 Automated Backups

Daily MySQL backup via cron at 03:00 AM:

mysqldump -u root -pPASSWORD confessionverse | gzip > /home/ubuntu/backups/backup_$(date +%F).sql.gz

Backups stored in:

/home/ubuntu/backups
📊 Logging

Production logging configuration:

spring.jpa.show-sql=false
logging.level.root=INFO

Debug logs disabled in production.

🔄 Rebuild After Code Changes
./mvnw clean package -DskipTests
docker stop confessionverse-backend
docker rm confessionverse-backend
docker build -t confessionverse-backend .
docker run ...
🧠 Production Notes

Backend no longer runs via systemd.

No java -jar manual execution.

Secrets managed via environment variables.

Docker restart policy enabled.

Ready for future migration to:

AWS RDS

Nginx reverse proxy

SSL (Let's Encrypt)

CI/CD pipeline

AWS ECR

📌 Status

Backend is production-ready for single-instance deployment.
