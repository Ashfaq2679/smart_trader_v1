# smart_trader_v1

Smart Trader V1 is an automated cryptocurrency trading analysis system built on
the Coinbase Advanced Trade API. It analyses price-action candles, detects chart
patterns, evaluates trend direction, and generates BUY / SELL / HOLD signals with
confidence scores and risk-managed position sizing.

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