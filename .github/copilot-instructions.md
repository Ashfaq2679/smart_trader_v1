# Copilot Instructions — smart_trader_v1

## Build & Test

```bash
# Build (skips integration tests)
mvn clean package -Dtest='!SmartTraderV1ApplicationTests'

# Run all unit tests
mvn test -Dtest='!SmartTraderV1ApplicationTests'

# Run a single test class
mvn test -Dtest=UserControllerTest

# Run a specific test method
mvn test -Dtest=UserControllerTest#createUser_returnsCreated
```

`SmartTraderV1ApplicationTests` requires a full application context (MongoDB, OAuth JWKS) and is always excluded from standard runs. Integration tests tagged with the `integration` JUnit group are also excluded by the Surefire plugin configuration.

---

## Architecture Overview

Smart Trader V1 is a **multi-user Spring Boot 3.4.4 / Java 17** application that fetches candlestick data from the Coinbase Advanced Trade API, runs technical analysis, and returns BUY/SELL/HOLD signals.

### Request flow

```
JWT Bearer → SecurityFilterChain
               └─▶ REST Controllers (/api/**)
                     └─▶ Service Layer
                           ├─▶ CoinbasePublicServiceImpl  ──▶ Coinbase API (public)
                           ├─▶ CoinbaseClientFactory      ──▶ Coinbase API (per-user, authenticated)
                           │     └─▶ CredentialEncryptionService (AES-256-GCM) ──▶ MongoDB
                           └─▶ TradingOrchestrator
                                 └─▶ PriceActionStrategy
                                       ├─▶ SupportResistanceDetector
                                       ├─▶ TrendAnalyzer
                                       ├─▶ CandlePatternDetector (22 patterns)
                                       └─▶ RiskManager
```

### Key layers

| Package | Role |
|---------|------|
| `controllers/` | REST endpoints — inline try/catch for business errors |
| `services/` | Business logic; `TradingOrchestrator` coordinates analysis |
| `strategy/` | Pluggable analysis algorithms; `PriceActionStrategy` is current impl |
| `models/` | Domain objects — MongoDB docs + in-memory analysis objects |
| `repositories/` | Spring Data MongoDB interfaces |
| `config/` | Security (JWT), caching (Caffeine), scheduling, Redis |
| `exceptions/` | `GlobalExceptionHandler` — catch-all for unhandled exceptions |
| `scheduler/` | `MarketScanScheduler` — hourly scheduled scans via `@Scheduled` |

### Persistence

MongoDB `coinbase_v1` database with collections: `users`, `user_preferences`, `user_credentials`, `orders`. Credentials are stored **AES-256-GCM encrypted** — never plaintext.

### Caching

- **Caffeine** (Spring Cache): 5-minute TTL, 1000-entry max. Used for `listPublicProducts()` via `@Cacheable`.
- **CoinbaseClientFactory**: Caffeine cache with 30-minute TTL, 500-entry max for per-user Coinbase clients.
- **Redis**: Optional. Enabled via `application.properties` (`spring.redis.host/port`). Falls back gracefully if unavailable.

---

## Key Conventions

### Coding Style and architectural conventions for Smart Trader V1:
Use Spring Boot 3.x
Use Java 21
Use constructor injection
Use MapStruct for mappings
Use KafkaTemplate for publishing
Use Caffeine for local caching
Use Redis for distributed caching

### Testing

- All tests use `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` + `@Mock`. **No `@SpringBootTest`** in unit tests.
- Tests are organised with `@Nested` inner classes and `@DisplayName` labels:
  ```java
  @Nested
  @DisplayName("POST /api/users — createUser")
  class CreateUserTests { ... }
  ```
- AssertJ (`assertThat(...)`) is preferred over JUnit assertions.

### Controllers

- Every endpoint is annotated with `@RateLimiter(name = "apiRateLimiter")` (Resilience4j, 100 req/s).
- Controllers catch `IllegalArgumentException` locally and return `Map.of("error", message)` bodies. `GlobalExceptionHandler` is the fallback for anything else.
- Error response shape: `{"timestamp", "status", "error", "message"}`.
- OpenAPI annotations (`@Operation`, `@ApiResponses`, `@Tag`) are required on every endpoint.

### Models

- Lombok `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` are used throughout.
- `MyCandle` uses a hand-rolled nested `Builder` (not Lombok) that calls `computeFields()` on `build()` to derive body size, wick percentages, colour, and candle type classifications automatically.
- `User.userId` is the MongoDB `@Id` field — `UserRepository` uses `findById/existsById/deleteById` directly. Other repositories (preferences, credentials) store userId as a plain field and expose `findByUserId/existsByUserId/deleteByUserId`.

### Strategy / Indicators

- New trading strategies must implement `TradingStrategy`:
  ```java
  TradeDecision analyze(List<MyCandle> candles, String productId);
  String getName();
  ```
- Planned technical indicators (RSI, MACD, EMA, etc.) should implement:
  ```java
  IndicatorResult calculate(List<MyCandle> candles);
  String getName();
  ```
  Place them in `strategy/` alongside existing detectors.
- `PriceActionStrategy` requires ≥ 20 candles; returns `HOLD` with 0.0 confidence otherwise.
- Confidence scoring: base 0.3 + alignment bonuses, capped at 1.0. Risk assessment only runs when confidence ≥ 0.6.

### Security

- The app is a **stateless OAuth 2.0 Resource Server**. It does not issue tokens.
- All `/api/**` requires `Authorization: Bearer <JWT>`. `/actuator/health` is public. All other paths are denied.
- JWKS URI is configured via `OAUTH2_JWK_SET_URI` env var (required at startup).
- CSRF is disabled (stateless Bearer token API).

### Configuration

- `spring.main.allow-circular-references = true` is set intentionally — do not remove it.
- `candles.ignore.names: BTC-USDC,ETH-USDC` excludes these pairs from market scans.
- Resilience4j circuit breaker instances are named `orderService` and `coinbasePublicService`.

### Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `CREDENTIAL_ENCRYPTION_KEY` | Yes | Base64-encoded 256-bit AES key |
| `OAUTH2_JWK_SET_URI` | Yes | JWKS endpoint of the OAuth 2.0 provider |
| `SPRING_DATA_MONGODB_URI` | Docker only | Overrides `application.properties` MongoDB settings |
