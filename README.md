# smart_trader_v1

Smart Trader V1 is an automated cryptocurrency trading analysis system built on
the Coinbase Advanced Trade API. It analyses price-action candles, detects chart
patterns, evaluates trend direction, and generates BUY / SELL / HOLD signals with
confidence scores and risk-managed position sizing.

## Java API source-code Reference##
```html
https://github.com/coinbase-samples/advanced-sdk-java
```
The ``apikeyname`` and ``privatekey`` for the Java SDK are generated via the Coinbase Developer Platform (CDP) portal using ECDSA cryptographic keys. Users must create a API key pair in the portal, securely saving the one-time viewable private key and the specific key name format for use in the Credentials object. For full setup instructions, visit 
``https://docs.cdp.coinbase.com/coinbase-business/authentication-authorization/api-key-authentication``


###Creating API Keys###
- Navigate to the Coinbase Developer Platform and select Secret API Keys under the API Keys tab.
- Click the Create API key button.
- Enter an API key nickname and then expand API restrictions and Advanced Settings.
- Enter your IPs in the IP allowlist section (recommended but not required).
- Set portfolio and permission restrictions.
- Change signature algorithm to ECDSA if you’re not developing your own authentication code.
- Click Create API key.
- Secure your private/public key pair in a safe location.

###Configuring portfolios###
You can create a separate portfolio for algo trading.
Once you create a portfolio you need to configure it in ``smart_trader_v1`` application in order to keep these trades separate.
- To configure a portfolio with the ``smart_trader_v1`` application, you need to get the ``UUID`` of the portfolio.
- To get the ``UUID`` of the portfolio, you need to get the JWT token to call the ``ListPortfolios`` API 
- To get the JWT, you need to use on of the ways given on this documentation page `https://docs.cdp.coinbase.com/get-started/authentication/jwt-authentication#code-samples-for-ecdsa-keys`
- Follow the instructions based on the language of your choice.

####List portfolios####
``https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/portfolios/list-portfolios``

## Quick Start

### Prerequisites

- **Java 17** (or later)
- **Maven 3.9+**
- **MongoDB 7** (local or Docker)
- **An OAuth 2.0 Authorization Server** (Auth0, Keycloak, Okta, etc.)

### Build & Test

```bash
mvn clean package -Dtest='!SmartTraderV1ApplicationTests'
```

### Run Locally

```bash
export CREDENTIAL_ENCRYPTION_KEY=<base64-encoded-256-bit-AES-key>
export OAUTH2_JWK_SET_URI=<your-jwks-uri>
java -jar target/smart-trader-v1-0.0.1-SNAPSHOT.jar
```

### Run with Docker Compose

```bash
export CREDENTIAL_ENCRYPTION_KEY=<base64-encoded-256-bit-AES-key>
export OAUTH2_JWK_SET_URI=<your-jwks-uri>
docker compose up --build
```

---

## OAuth 2.0 Security

Smart Trader V1 acts as an **OAuth 2.0 Resource Server**. All `/api/**` endpoints
require a valid JWT Bearer token. The application does **not** issue tokens itself — it
validates tokens issued by an external authorization server using the server's
JSON Web Key Set (JWKS) endpoint.

### How It Works

1. A client authenticates with your OAuth 2.0 authorization server and obtains a JWT access token.
2. The client includes the token in every request to Smart Trader:
   ```
   Authorization: Bearer <access-token>
   ```
3. Smart Trader validates the JWT signature by fetching the public keys from the JWKS URI.
4. If the token is valid, the request proceeds; otherwise, a `401 Unauthorized` response is returned.

### Configuration

Set the JWKS URI for your authorization server via the `OAUTH2_JWK_SET_URI` environment variable
(or directly in `application.properties`):

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${OAUTH2_JWK_SET_URI}
```

#### Provider-Specific JWKS URIs

| Provider | JWKS URI Format |
|----------|-----------------|
| **Auth0** | `https://<tenant>.auth0.com/.well-known/jwks.json` |
| **Keycloak** | `http://<host>:<port>/realms/<realm>/protocol/openid-connect/certs` |
| **Okta** | `https://<domain>.okta.com/oauth2/default/v1/keys` |
| **Azure AD** | `https://login.microsoftonline.com/<tenant-id>/discovery/v2.0/keys` |
| **Google** | `https://www.googleapis.com/oauth2/v3/certs` |

### Steps to Enable OAuth 2.0

1. **Choose an authorization server** — Auth0, Keycloak, Okta, or any OAuth 2.0 / OpenID Connect provider.

2. **Register a new API / Resource Server** in your authorization server:
   - Set the **audience** (identifier) to your Smart Trader deployment URL (e.g. `https://api.smarttrader.example.com`).
   - Note the **JWKS URI** from the provider's documentation.

3. **Register a client application** that will call Smart Trader:
   - Use the **Client Credentials** grant type for service-to-service communication.
   - Use the **Authorization Code** grant type (with PKCE) for user-facing clients.
   - Grant the client permission to request tokens for your API audience.

4. **Set the environment variable** before starting Smart Trader:
   ```bash
   export OAUTH2_JWK_SET_URI=https://<your-provider>/.well-known/jwks.json
   ```

5. **Start the application** — Smart Trader will validate incoming JWTs against the configured JWKS URI.

6. **Obtain a token** from your authorization server and include it in requests:
   ```bash
   curl -H "Authorization: Bearer <access-token>" http://localhost:8080/api/users/user-1
   ```

### Endpoint Access Rules

| Path | Access |
|------|--------|
| `/api/**` | Authenticated (valid JWT required) |
| `/actuator/health` | Public (no authentication needed) |
| All other paths | Denied |

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `CREDENTIAL_ENCRYPTION_KEY` | Yes | Base64-encoded 256-bit AES key for credential encryption |
| `OAUTH2_JWK_SET_URI` | Yes | JWKS endpoint of your OAuth 2.0 authorization server |
| `SPRING_DATA_MONGODB_URI` | Docker only | MongoDB connection string (Docker Compose sets this) |

---

## Redis (Optional) - Local development and Docker

Smart Trader can use Redis for lightweight caching (qty aggregates, recent-orders lists) to reduce DB load. Redis is optional but recommended for production-like behavior.

### Run via Docker (recommended for development)

Quick start using the official Redis image:

```bash
# starts Redis on localhost:6379
docker run -d --name smarttrader-redis -p 6379:6379 redis:7
```

To run a Redis container with persistence (data saved to ./redis-data):

```bash
mkdir -p ./redis-data
docker run -d --name smarttrader-redis -p 6379:6379 -v $(pwd)/redis-data:/data redis:7 redis-server --appendonly yes
```

To stop and remove the container:

```bash
docker stop smarttrader-redis && docker rm smarttrader-redis
```

### Local installation (Linux / macOS / Windows)

- macOS (Homebrew):
  - brew install redis
  - brew services start redis

- Linux (Debian/Ubuntu):
  - sudo apt update
  - sudo apt install redis-server
  - sudo systemctl enable --now redis-server

- Windows:
  - Use Microsoft-supported WSL2 (Ubuntu) and install via Linux instructions, or download Redis for Windows from Memurai or MS build variants. Running via Docker is simplest on Windows.

Verify Redis is running:

```bash
redis-cli ping
# should reply: PONG
```

### Configuration for Smart Trader

Set Redis connection properties in application.properties or application.yml. Example application.properties entries:

```properties
spring.redis.host=localhost
spring.redis.port=6379
# spring.redis.password=yourpassword   # set if you secure Redis
```

The project contains a RedisConfig that wires a LettuceConnectionFactory and a StringRedisTemplate bean. Restart the application after configuring Redis.

### Notes and troubleshooting

- If Redis is unavailable, Smart Trader falls back to DB aggregations for cache misses, but performance may be reduced.
- On Windows prefer Docker or WSL2 to avoid unsupported native builds.
- For production use consider managed Redis (AWS ElastiCache) and secure access between app and Redis (VPC, auth/ACLs, TLS).

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/users` | Create a new user |
| `GET` | `/api/users/{userId}` | Get user details |
| `PUT` | `/api/users/{userId}` | Update user |
| `DELETE` | `/api/users/{userId}` | Delete user |
| `GET` | `/api/users/{userId}/preferences` | Get user preferences |
| `PUT` | `/api/users/{userId}/preferences` | Set user preferences |
| `DELETE` | `/api/users/{userId}/preferences` | Reset user preferences |
| `POST` | `/api/credentials/{userId}` | Register Coinbase credentials |
| `GET` | `/api/credentials/{userId}/exists` | Check if credentials exist |
| `DELETE` | `/api/credentials/{userId}` | Remove credentials |
| `POST` | `/api/scanner/scan` | Trigger market scan |
| `GET` | `/api/scanner/results` | Get latest scan results |
| `GET` | `/api/scanner/analyze/{productId}` | Analyze a specific product |

All `/api/**` endpoints require a valid `Authorization: Bearer <token>` header.

---

## Documentation

- **[DESIGN.md](DESIGN.md)** — Detailed application design, architecture, and future plans
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — High-level architecture overview