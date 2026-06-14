# Smart Trader V1 - Low-Cost Deployment Guide

## 1. Deployment Goal

This deployment plan is optimized for running Smart Trader V1 at the lowest possible cost while still keeping the application stable, secure, and easy to maintain.

Smart Trader V1 is a Spring Boot 3.4.4 application using Java 17, MongoDB 7, Caffeine cache, OAuth2/JWT security, Coinbase Advanced Trade API integration, and Docker/Docker Compose. The current architecture already supports a simple containerized deployment with one application container and one MongoDB container.

## 2. Recommended MVP Hosting Choice

For the first production/MVP version, use a single low-cost or free-tier VM instead of managed cloud services.

Recommended options:

1. Oracle Cloud Always Free VM
2. AWS EC2 Free Tier or lowest-cost EC2 instance
3. Google Cloud/Azure free-tier VM
4. Low-cost VPS provider if cloud credits are not available

Avoid these for MVP unless usage grows significantly:

- AWS ECS/Fargate
- AWS MSK/Kafka
- AWS DocumentDB
- Kubernetes/EKS
- Managed load balancers
- Multi-node clusters

## 3. Target Deployment Architecture

```text
Internet
   |
   v
Cloudflare DNS / Optional CDN
   |
   v
Nginx Reverse Proxy + HTTPS
   |
   v
Docker Compose Host VM
   |
   |-- smart-trader-v1 Spring Boot container
   |-- MongoDB 7 container
   |-- Docker volume for MongoDB data
   |-- Local log files with rotation
```

## 4. Minimum Server Requirements

Recommended minimum:

```text
CPU: 2 vCPU preferred
RAM: 2 GB minimum, 4 GB recommended
Disk: 30-50 GB SSD
OS: Ubuntu 22.04 LTS or 24.04 LTS
```

A 1 GB RAM instance may work for testing, but Spring Boot plus MongoDB can become unstable under scan load.

## 5. Runtime Components

| Component | Deployment Choice | Reason |
|---|---|---|
| Spring Boot App | Docker container | Portable and easy to restart |
| MongoDB | Docker container with persistent volume | Lowest cost for MVP |
| Scheduler | Spring `@Scheduled` | Already supported by app design |
| Cache | In-memory Caffeine | Already included; no Redis needed initially |
| Reverse Proxy | Nginx | Simple HTTPS and routing |
| SSL | Let's Encrypt Certbot | Free TLS certificates |
| Logs | Local files + Docker logs | Low cost |
| Backups | Cron + `mongodump` | Simple and cheap |

## 6. Environment Variables

Create a `.env` file on the VM. Do not commit this file to Git.

```env
SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/coinbase
CREDENTIAL_ENCRYPTION_KEY=REPLACE_WITH_BASE64_256_BIT_KEY
OAUTH2_JWK_SET_URI=https://your-auth-server/.well-known/jwks.json
JAVA_OPTS=-Xms256m -Xmx768m
```

Generate a 256-bit encryption key:

```bash
openssl rand -base64 32
```

Keep this key safe. If you lose it, encrypted Coinbase credentials stored in MongoDB cannot be decrypted.

## 7. Docker Compose File

Create `docker-compose.yml`:

```yaml
services:
  app:
    image: smart-trader-v1:latest
    container_name: smart-trader-v1
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_MONGODB_URI: ${SPRING_DATA_MONGODB_URI}
      CREDENTIAL_ENCRYPTION_KEY: ${CREDENTIAL_ENCRYPTION_KEY}
      OAUTH2_JWK_SET_URI: ${OAUTH2_JWK_SET_URI}
      JAVA_OPTS: ${JAVA_OPTS}
    depends_on:
      mongo:
        condition: service_healthy
    restart: unless-stopped
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"

  mongo:
    image: mongo:7
    container_name: smart-trader-mongo
    volumes:
      - mongo-data:/data/db
    ports:
      - "127.0.0.1:27017:27017"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 30s
      timeout: 10s
      retries: 5
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"

volumes:
  mongo-data:
```

For stronger security, remove the MongoDB port mapping entirely if you do not need direct host access.

## 8. Application Dockerfile

Use a multi-stage Dockerfile:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 9. VM Setup Steps

### 9.1 Install Docker

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

Log out and log back in.

### 9.2 Install Docker Compose Plugin

```bash
docker compose version
```

If missing:

```bash
sudo apt install -y docker-compose-plugin
```

### 9.3 Clone and Build

```bash
git clone <YOUR_REPOSITORY_URL> smart-trader-v1
cd smart-trader-v1
docker build -t smart-trader-v1:latest .
```

### 9.4 Start the Stack

```bash
docker compose up -d
```

### 9.5 Verify

```bash
docker ps
docker logs -f smart-trader-v1
curl http://localhost:8080/actuator/health
```

## 10. Nginx Reverse Proxy

Install Nginx:

```bash
sudo apt install -y nginx
```

Create `/etc/nginx/sites-available/smart-trader`:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable it:

```bash
sudo ln -s /etc/nginx/sites-available/smart-trader /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 11. HTTPS with Let's Encrypt

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

Auto-renewal test:

```bash
sudo certbot renew --dry-run
```

## 12. MongoDB Backup Plan

Create backup directory:

```bash
mkdir -p ~/backups/mongo
```

Create `backup-mongo.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

TS=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$HOME/backups/mongo"
mkdir -p "$BACKUP_DIR"

docker exec smart-trader-mongo mongodump --db coinbase --archive=/tmp/coinbase_$TS.archive --gzip
docker cp smart-trader-mongo:/tmp/coinbase_$TS.archive "$BACKUP_DIR/coinbase_$TS.archive"
docker exec smart-trader-mongo rm /tmp/coinbase_$TS.archive

find "$BACKUP_DIR" -type f -mtime +14 -delete
```

Make executable:

```bash
chmod +x backup-mongo.sh
```

Cron entry:

```bash
crontab -e
```

Add:

```cron
0 2 * * * /home/ubuntu/backup-mongo.sh >> /home/ubuntu/backups/mongo/backup.log 2>&1
```

For better safety, sync backups to a cheap object store later.

## 13. Security Checklist

- Keep `.env` outside Git.
- Use strong `CREDENTIAL_ENCRYPTION_KEY`.
- Keep only ports 80 and 443 open publicly.
- Do not expose MongoDB to the internet.
- Use OAuth2/JWT for `/api/**` endpoints.
- Keep `/actuator/health` public only if needed for health checks.
- Use Coinbase API keys with least privilege.
- Prefer view-only keys for analysis-only environments.
- Use trade permission only in controlled production environments.
- Enable firewall:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

## 14. Scheduling Strategy

Use the application's existing Spring scheduler for scans.

Recommended scan intervals:

| Environment | Interval |
|---|---|
| Development | 15-30 minutes |
| MVP Production | 5-15 minutes |
| High-frequency scanning | Move to queue/worker model later |

Avoid very frequent scans initially because public API rate limits and small VM resources can become bottlenecks.

## 15. Cost Control Rules

To keep cost close to free:

1. Use one free-tier VM.
2. Run MongoDB locally in Docker.
3. Use Caffeine instead of Redis.
4. Use Spring Scheduler instead of Kafka initially.
5. Use local logs instead of paid log services.
6. Use free Cloudflare DNS.
7. Use Let's Encrypt for SSL.
8. Avoid managed databases until the app has real usage.
9. Avoid Kubernetes.
10. Avoid managed Kafka/MSK until event throughput justifies it.

## 16. When to Upgrade Architecture

Upgrade only when one of these happens:

| Symptom | Upgrade |
|---|---|
| Scans take longer than schedule interval | Add worker service or queue |
| Many users run scans concurrently | Move to ECS/Fargate or multiple VMs |
| MongoDB CPU/RAM pressure | Move to MongoDB Atlas or dedicated DB VM |
| Need event-driven candle processing | Add Kafka/Redpanda |
| Need real-time price feed processing | Add WebSocket ingestion service |
| Need high availability | Use managed services + load balancer |

## 17. Suggested Future Low-Cost Scaling Path

Phase 1 - Current MVP:

```text
Single VM + Docker Compose + MongoDB container
```

Phase 2 - Slight Scale:

```text
Single VM + separate scanner worker container + same MongoDB
```

Phase 3 - Event Driven:

```text
Redpanda/Kafka container + scanner producer + strategy consumer
```

Phase 4 - Production Cloud:

```text
AWS ECS/Fargate + MongoDB Atlas + MSK/Redpanda Cloud + CloudWatch
```

## 18. Operational Commands

Start:

```bash
docker compose up -d
```

Stop:

```bash
docker compose down
```

Restart app only:

```bash
docker compose restart app
```

View logs:

```bash
docker logs -f smart-trader-v1
```

Check resource usage:

```bash
docker stats
```

Rebuild and redeploy:

```bash
git pull
docker build -t smart-trader-v1:latest .
docker compose up -d
```

## 19. Final Recommendation

For Smart Trader V1, the best low-cost deployment is:

```text
Oracle Cloud Always Free or low-cost VM
Docker Compose
Spring Boot app container
MongoDB 7 container
Nginx reverse proxy
Let's Encrypt SSL
Cron-based MongoDB backup
```

This keeps the system close to free while matching the current architecture and leaving a clear path to scale later.
