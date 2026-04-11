# Smart Trader V1 — Application Design & Future Plans

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Package Structure](#package-structure)
- [Component Details](#component-details)
  - [Models](#models)
  - [Controller Layer](#controller-layer-rest-api)
  - [Strategy Layer](#strategy-layer)
  - [Service Layer](#service-layer)
  - [Configuration & Repository](#configuration--repository)
- [Design Patterns](#design-patterns)
- [Multi-User Architecture](#multi-user-architecture)
- [Security](#security)
  - [OAuth 2.0 Resource Server (JWT)](#oauth-20-resource-server-jwt)
  - [Credential Encryption](#credential-encryption)
- [Testing](#testing)
- [Infrastructure](#infrastructure)
- [Future Plans — Technical Indicators](#future-plans--technical-indicators)
- [Future Plans — Platform Features](#future-plans--platform-features)

---

## Overview

Smart Trader V1 is an automated cryptocurrency trading analysis system built on the
Coinbase Advanced Trade API. It analyses price-action candles, detects chart
patterns, evaluates trend direction and generates BUY / SELL / HOLD signals with
confidence scores and risk-managed position sizing.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| Build | Maven |
| Database | MongoDB 7 (Spring Data) |
| Cache | Caffeine (Spring Cache) |
| Security | Spring Security 6 + OAuth 2.0 Resource Server (JWT) |
| Exchange SDK | Coinbase Advanced SDK 0.1.0 / Core 1.0.1 |
| Testing | JUnit 5 + Mockito + AssertJ + Spring Security Test |
| Code Gen | Lombok |
| Container | Docker (multi-stage), Docker Compose |

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      Spring Boot App                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────── OAuth 2.0 Security (JWT) ────────────────┐  │
│  │ SecurityConfig → SecurityFilterChain                │  │
│  │   • /api/** — authenticated (Bearer JWT)            │  │
│  │   • /actuator/health — public (health checks)       │  │
│  │   • Token validation via JWKS URI                   │  │
│  └──────────────────────┬─────────────────────────────┘  │
│                          │ (authenticated requests only)  │
│  ┌────────────────────── REST Controllers ─────────────┐ │
│  │ UserController          /api/users                  │ │
│  │ UserPreferencesCtrl     /api/users/{id}/preferences │ │
│  │ CredentialController    /api/credentials             │ │
│  │ MarketScanController    /api/scanner                 │ │
│  └──────────────────────────┬──────────────────────────┘ │
│                              │                           │
│  ┌───────────── Service Layer ──────────────────────┐    │
│  │ UserService           UserPreferencesService     │    │
│  │ ClientService ──▶ CoinbaseClientFactory          │    │
│  │ TradingOrchestrator   MarketScannerService       │    │
│  │ CredentialEncryptionService   CoinbasePublicSvc  │    │
│  └──────────────────────┬───────────────────────────┘    │
│                          │                               │
│  ┌───────── Strategy Layer ─────────────────────────┐    │
│  │ PriceActionStrategy  TrendAnalyzer  RiskManager  │    │
│  │ CandlePatternDetector  SupportResistanceDetector │    │
│  └──────────────────────────────────────────────────┘    │
│                                                          │
│  ┌───────── Repository Layer ───────────────────────┐    │
│  │ UserRepository  UserPreferencesRepository        │    │
│  │ UserCredentialsRepository                        │    │
│  └──────────────────────────────────────────────────┘    │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  MongoDB (users, user_preferences, user_credentials,     │
│           orders)                                        │
│  Coinbase Advanced Trade API                             │
│  External OAuth 2.0 Authorization Server (JWT issuer)    │
└──────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.techcobber.smarttrader.v1
├── config/
│   ├── AppConfig.java               # Caffeine cache beans
│   ├── SecurityConfig.java          # OAuth 2.0 resource server (JWT)
│   └── SchedulingConfig.java        # @EnableScheduling
├── controllers/
│   ├── UserController.java          # REST CRUD for users (/api/users)
│   ├── UserPreferencesController.java # REST CRUD for preferences (/api/users/{id}/preferences)
│   ├── CredentialController.java    # REST credential management (/api/credentials)
│   └── MarketScanController.java    # REST scan & analysis (/api/scanner)
├── models/
│   ├── MyCandle.java                 # OHLCV candle with pattern detection (Builder)
│   ├── TradeDecision.java            # BUY/SELL/HOLD signal + confidence (Builder)
│   ├── User.java                     # User profile document (MongoDB)
│   ├── UserPreferences.java          # Per-user trading configuration (MongoDB)
│   ├── UserCredentials.java          # Encrypted credential document (MongoDB)
│   ├── CoinScanResult.java           # Market scan result with profit-potential score
│   ├── Order.java                    # Trade execution record (MongoDB)
│   └── ListCandles.java              # Candle list wrapper for JSON deserialization
├── repositories/
│   ├── UserRepository.java           # MongoDB CRUD for User
│   ├── UserPreferencesRepository.java # MongoDB CRUD for UserPreferences
│   └── UserCredentialsRepository.java # MongoDB CRUD for UserCredentials
├── scheduler/
│   └── MarketScanScheduler.java      # @Scheduled hourly market scans
├── services/
│   ├── UserService.java              # User lifecycle management
│   ├── UserPreferencesService.java   # Preferences CRUD logic
│   ├── ClientService.java            # Façade over CoinbaseClientFactory
│   ├── CoinbaseClientFactory.java    # Per-user client creation + Caffeine cache
│   ├── CredentialEncryptionService.java # AES-256-GCM encrypt/decrypt
│   ├── CoinbasePublicServiceImpl.java   # Market data (extends SDK PublicServiceImpl)
│   ├── MarketScannerService.java     # USDC-paired coin scanning + ranking
│   └── TradingOrchestrator.java      # Analysis orchestration + risk management
├── strategy/
│   ├── TradingStrategy.java          # Strategy interface (analyze, getName)
│   ├── PriceActionStrategy.java      # Price-action analysis implementation
│   ├── TrendAnalyzer.java            # Swing-high/low trend detection
│   ├── CandlePatternDetector.java    # 22 candlestick pattern types
│   ├── SupportResistanceDetector.java # S/R level clustering
│   └── RiskManager.java              # Position sizing, SL/TP, R:R validation
└── SmartTraderV1Application.java     # Spring Boot entry point
```

---

## Component Details

### Models

| Model | Storage | Purpose |
|-------|---------|---------|
| `MyCandle` | In-memory | OHLCV data with auto-detected candlestick patterns. Builder pattern. Computes body size, wick percentages, colour, and 1/2/3-candle patterns. |
| `TradeDecision` | In-memory | Immutable signal output: BUY/SELL/HOLD, confidence (0–1), reasoning, detected patterns, trend direction, nearest S/R levels. |
| `User` | MongoDB | Platform user profile: userId serves as `@Id` (mapped to MongoDB `_id`), email, displayName, enabled flag, timestamps. Stored in the `users` collection. |
| `UserPreferences` | MongoDB | Per-user config: strategy name, granularity, asset pair, position size %, max daily loss, timezone, enabled flag. Stored in the `user_preferences` collection. |
| `UserCredentials` | MongoDB | AES-GCM encrypted Coinbase credential blobs. Unique index on `userId`. |
| `CoinScanResult` | In-memory | Market scan result with trade decision, profit-potential score (0–100), and summary. |
| `Order` | MongoDB | Trade execution records: userId, timestamp, product, price, qty, side, decision factors. |
| `ListCandles` | In-memory | JSON deserialization wrapper for Coinbase candle responses. |

### Controller Layer (REST API)

#### `UserController` — `/api/users`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/users` | Create a new user (201 on success, 409 if duplicate) |
| `GET` | `/api/users/{userId}` | Retrieve a user by userId (404 if not found) |
| `PUT` | `/api/users/{userId}` | Update user fields (email, displayName, enabled) |
| `DELETE` | `/api/users/{userId}` | Delete a user |

#### `UserPreferencesController` — `/api/users/{userId}/preferences`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/users/{userId}/preferences` | Retrieve preferences for a user |
| `PUT` | `/api/users/{userId}/preferences` | Create or update preferences (upsert) |
| `DELETE` | `/api/users/{userId}/preferences` | Delete (reset) preferences |

#### `CredentialController` — `/api/credentials`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/credentials/{userId}` | Register or update Coinbase credentials |
| `GET` | `/api/credentials/{userId}/exists` | Check whether credentials exist |
| `DELETE` | `/api/credentials/{userId}` | Remove stored credentials |

#### `MarketScanController` — `/api/scanner`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/scanner/scan` | Trigger an on-demand market scan |
| `GET` | `/api/scanner/results` | Retrieve cached results from the latest scan |
| `GET` | `/api/scanner/analyze/{productId}` | Analyze a specific product |

### Strategy Layer

#### `TradingStrategy` (interface)

```java
TradeDecision analyze(List<MyCandle> candles);
String getName();
```

All trading strategies implement this contract, enabling the orchestrator to swap
strategies without code changes.

#### `PriceActionStrategy`

The primary strategy. Requires ≥ 20 candles. Workflow:

1. **S/R Detection** — 50-candle lookback, levels clustered within 0.5 % tolerance
2. **Trend Analysis** — 20-candle lookback, swing-high/low counting
3. **Pattern Detection** — 22 candlestick pattern types across 1/2/3-candle windows
4. **Signal Generation** —
   - **BUY**: breakout above resistance + uptrend + bullish patterns, _or_ bounce at support
   - **SELL**: breakdown below support + downtrend + bearish patterns, _or_ rejection at resistance
   - **HOLD**: no strong confluence (default)
5. **Confidence Scoring** — base 0.3 + trend alignment + pattern count + S/R proximity → capped at 1.0
6. **Reasoning** — human-readable explanation string

#### `TrendAnalyzer`

Extracts swing highs/lows, counts higher-high / lower-low sequences.
Returns `TrendResult(direction, strength, description)` where direction is
`UP | DOWN | SIDEWAYS` and strength is 0.0–1.0.

#### `CandlePatternDetector`

Detects 22 candlestick patterns across three windows:

| Window | Patterns |
|--------|----------|
| Single | Hammer, Inverted Hammer, Shooting Star, Hanging Man, Doji variants, Spinning Top, Marubozu |
| Two | Engulfing, Harami, Piercing Line, Dark Cloud Cover, Tweezer Top/Bottom |
| Three | Morning Star, Evening Star, Three White Soldiers, Three Black Crows |

Classifies overall bias as `BULLISH`, `BEARISH`, or `NEUTRAL`.

#### `SupportResistanceDetector`

Clusters swing-high/low prices within 0.5 % tolerance, counts "touches" for
strength scoring, returns `Level(price, type, strength)` sorted by strength.

#### `RiskManager`

Position sizing and trade validation:

- **Position size** = `accountBalance × (positionSize% / 100) / currentPrice`
- **Stop-loss** = nearest S/R level ± 0.5 %, or default ± 2 %
- **Take-profit** = 2 × risk per unit (1 : 2 R:R)
- **Approval** = potential loss ≤ max daily loss

---

### Service Layer

#### `UserService`

User lifecycle management.

| Method | Description |
|--------|-------------|
| `createUser(user)` | Creates a new user (validates unique userId, sets timestamps) |
| `getUser(userId)` | Retrieves a user by userId |
| `updateUser(userId, updates)` | Updates mutable fields (email, displayName, enabled) |
| `deleteUser(userId)` | Deletes a user |
| `userExists(userId)` | Checks whether a user exists |

#### `UserPreferencesService`

Per-user trading preference management.

| Method | Description |
|--------|-------------|
| `getPreferences(userId)` | Retrieves preferences for a user |
| `savePreferences(userId, updates)` | Creates or updates preferences (upsert semantics) |
| `deletePreferences(userId)` | Deletes preferences for a user |

#### `TradingOrchestrator`

Central orchestration service.

| Method | Description |
|--------|-------------|
| `executeAnalysis(candles, productId)` | Runs PriceActionStrategy, returns TradeDecision |
| `executeWithRisk(candles, productId, prefs, balance)` | Analysis + RiskManager assessment (if confidence ≥ 0.6) |

#### `ClientService`

Façade delegating to `CoinbaseClientFactory`:

| Method | Description |
|--------|-------------|
| `getClientForUser(userId)` | Returns per-user CoinbaseAdvancedClient |
| `registerCredentials(userId, rawCreds)` | Encrypts and stores credentials |
| `removeCredentials(userId)` | Deletes credentials, evicts cache |
| `hasCredentials(userId)` | Checks if user has stored credentials |

#### `CoinbaseClientFactory`

Factory + Cache-Aside pattern. Builds `CoinbaseAdvancedClient` per user from
encrypted MongoDB credentials. Caffeine cache with 30-minute TTL and 500-entry max.
Cache automatically invalidated on credential update/removal.

#### `CredentialEncryptionService`

AES-256-GCM encryption with random 12-byte IV per operation. Key sourced from
`CREDENTIAL_ENCRYPTION_KEY` environment variable (Base64-encoded 256-bit).

#### `CoinbasePublicServiceImpl`

Extends Coinbase SDK `PublicServiceImpl` (Template Method pattern). Provides:

| Method | Description |
|--------|-------------|
| `fetchPublicProduct(productId)` | Product details |
| `fetchCandles(productId, start, end, granularity)` | OHLCV candle data |
| `getPrice(productId)` | Current price |
| `getFilteredProducts(filter)` | Filter products by ID substring |
| `getTopVolumeProductsInLast24Hrs(limit)` | Top volume products |
| `getTop/BottomVolumeChangeProductsInLast24Hrs(limit, filter)` | Volume movers |
| `getTop/BottomPriceChangeProductsInLast24Hrs(limit, filter)` | Price movers |
| `listPublicProducts()` | All products (cached via `@Cacheable`) |

---

### Configuration & Repository

#### `SecurityConfig`

OAuth 2.0 Resource Server configuration:
- `securityFilterChain()` — Stateless session policy, JWT-based authentication
- `/api/**` — requires a valid Bearer JWT
- `/actuator/health` — permitted without authentication (health checks)
- All other paths — denied
- CSRF disabled (stateless API)
- Token validation via JWKS URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)

#### `AppConfig`

Spring `@Configuration` providing Caffeine cache infrastructure:
- `caffeineConfig()` — 5-minute TTL, 1000-entry max
- `cacheManager()` — Spring `CaffeineCacheManager`

#### `UserRepository`

Spring Data MongoDB interface for `User`. Since `userId` is the `@Id` field,
the built-in `findById`, `existsById`, and `deleteById` methods from
`MongoRepository` operate directly on the user's unique identifier — no custom
query methods are needed.

#### `UserPreferencesRepository`

Spring Data MongoDB interface for `UserPreferences`:
- `findByUserId(String)` — lookup by userId
- `existsByUserId(String)` — existence check
- `deleteByUserId(String)` — remove preferences

#### `UserCredentialsRepository`

Spring Data MongoDB interface:
- `findByUserId(String)` — lookup by user
- `existsByUserId(String)` — existence check
- `deleteByUserId(String)` — remove credentials

---

## Design Patterns

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | `TradingStrategy` / `PriceActionStrategy` | Interchangeable analysis algorithms |
| **Builder** | `MyCandle.Builder`, `TradeDecision` (Lombok) | Fluent immutable object construction |
| **Template Method** | `CoinbasePublicServiceImpl` extends `PublicServiceImpl` | SDK HTTP infrastructure reuse |
| **Factory + Cache-Aside** | `CoinbaseClientFactory` | Per-user client creation with caching |
| **Façade** | `ClientService` | Simplified API over factory internals |
| **Factory Method** | `AppConfig` `@Bean` methods | Managed bean creation |
| **Filter Chain** | `SecurityConfig` / `SecurityFilterChain` | OAuth 2.0 JWT authentication pipeline |

---

## Multi-User Architecture

```
User A ──registerCredentials("A", creds)──▶ ┌─────────────┐
                                            │ Credential   │ encrypt
                                            │ Encryption   │──────▶ MongoDB
                                            │ Service      │        (user_credentials)
User B ──registerCredentials("B", creds)──▶ └─────────────┘

User A ──getClientForUser("A")──▶ ┌──────────────────┐ cache hit ──▶ CoinbaseClient(A)
                                  │ CoinbaseClient   │
                                  │ Factory           │ cache miss ──▶ decrypt ──▶ build ──▶ cache
User B ──getClientForUser("B")──▶ └──────────────────┘ ──▶ CoinbaseClient(B)
```

- Each user's credentials are encrypted separately with unique IVs
- Each user gets an isolated `CoinbaseAdvancedClient` instance
- Clients are cached for 30 minutes, max 500 concurrent users
- No user can access another user's credentials or client

---

## Security

### OAuth 2.0 Resource Server (JWT)

The application acts as an **OAuth 2.0 Resource Server**. Every request to `/api/**`
must include a valid JWT Bearer token in the `Authorization` header. The token is
validated against the authorization server's JSON Web Key Set (JWKS) endpoint.

| Concern | Approach |
|---------|----------|
| Authentication | OAuth 2.0 Bearer JWT tokens validated via JWKS URI |
| Session management | Stateless — no server-side sessions |
| CSRF | Disabled (stateless API with Bearer tokens) |
| Public endpoints | `/actuator/health` only (for container health checks) |
| Default deny | All non-API, non-health paths are denied |

### Credential Encryption

| Concern | Approach |
|---------|----------|
| Credentials at rest | AES-256-GCM encrypted in MongoDB |
| Unique IVs | 12-byte random IV per encryption prevents pattern analysis |
| Tamper detection | GCM authentication tag validates ciphertext integrity |
| Key management | `CREDENTIAL_ENCRYPTION_KEY` env var (Base64 256-bit) |
| User isolation | Per-user client instances, no shared state |
| Cache eviction | Credentials update/removal invalidates cached clients |

---

## Testing

- **Framework**: JUnit 5 + Mockito + AssertJ + Spring Security Test (no `@SpringBootTest`)
- **Run**: `mvn test -Dtest='!SmartTraderV1ApplicationTests'`
- **Total**: 309 tests across 26 test files

| Area | Test File | Tests |
|------|-----------|-------|
| Models | `MyCandleTest` | 41 (patterns, builder, computed fields) |
| Models | `TradeDecisionTest` | 9 (builder, enum, equals) |
| Models | `UserTest` | 8 (getters, setters, equals, toString) |
| Models | `UserPreferencesTest` | 4 |
| Models | `OrderTest` | 4 |
| Models | `ListCandlesTest` | 3 |
| Models | `UserCredentialsTest` | 4 |
| Models | `CoinScanResultTest` | 17 (score calculation, builder, summary) |
| Strategy | `PriceActionStrategyTest` | 14 (buy/sell/hold signals, confidence) |
| Strategy | `CandlePatternDetectorTest` | 20 (1/2/3-candle, bias, edge cases) |
| Strategy | `SupportResistanceDetectorTest` | 9 (levels, grouping, strength) |
| Strategy | `TrendAnalyzerTest` | 9 (up/down/sideways, strength) |
| Strategy | `RiskManagerTest` | 12 (position, SL/TP, daily loss) |
| Services | `UserServiceTest` | 11 (create, get, update, delete, exists) |
| Services | `UserPreferencesServiceTest` | 8 (get, save/upsert, delete) |
| Services | `CoinbasePublicServiceImplTest` | 25 (filtering, sorting, caching) |
| Services | `TradingOrchestratorTest` | 9 (analysis, risk integration) |
| Services | `ClientServiceTest` | 6 (delegation to factory) |
| Services | `CoinbaseClientFactoryTest` | 11 (register, build, remove, cache) |
| Services | `CredentialEncryptionServiceTest` | 9 (round-trip, IV uniqueness, tamper) |
| Services | `MarketScannerServiceTest` | 14 (scanning, ranking, filtering) |
| Controllers | `UserControllerTest` | 11 (CRUD, error handling, 404/409/500) |
| Controllers | `UserPreferencesControllerTest` | 8 (get, save, delete, error handling) |
| Controllers | `CredentialControllerTest` | 8 (register, check, remove, errors) |
| Controllers | `MarketScanControllerTest` | 8 (scan, results, analyze, errors) |
| Config | `SecurityConfigTest` | 4 (instantiation, annotations, bean) |

---

## Infrastructure

### Docker Compose

```yaml
services:
  app:    # Spring Boot on port 8080
  mongo:  # MongoDB 7 with health check
```

**Environment variables**:
- `SPRING_DATA_MONGODB_URI` — MongoDB connection string
- `CREDENTIAL_ENCRYPTION_KEY` — Base64 AES-256 key for credential encryption
- `OAUTH2_JWK_SET_URI` — JWKS endpoint of the OAuth 2.0 authorization server

### Dockerfile

Multi-stage build: Maven 3.9 + Temurin 17 (build) → Temurin 17 JRE (runtime).

---

## Future Plans — Technical Indicators

The current `PriceActionStrategy` relies solely on candlestick patterns, trend
analysis, and support/resistance levels. The next phase adds **technical
indicators** as confirmation signals to improve signal accuracy and reduce false
positives.

### Planned Indicators

Each indicator will be a standalone class in the `strategy/` package implementing
a common interface:

```java
public interface TechnicalIndicator {
    IndicatorResult calculate(List<MyCandle> candles);
    String getName();
}
```

#### Phase 1 — Momentum & Trend Confirmation

| Indicator | Class | Purpose | Parameters |
|-----------|-------|---------|------------|
| **RSI** (Relative Strength Index) | `RsiIndicator` | Overbought/oversold detection | Period: 14 |
| **MACD** (Moving Average Convergence Divergence) | `MacdIndicator` | Trend momentum + crossovers | Fast: 12, Slow: 26, Signal: 9 |
| **EMA** (Exponential Moving Average) | `EmaIndicator` | Dynamic support/resistance | Periods: 9, 21, 50, 200 |
| **SMA** (Simple Moving Average) | `SmaIndicator` | Trend direction baseline | Periods: 20, 50, 200 |

#### Phase 2 — Volatility & Volume Confirmation

| Indicator | Class | Purpose | Parameters |
|-----------|-------|---------|------------|
| **Bollinger Bands** | `BollingerBandsIndicator` | Volatility squeeze/expansion | Period: 20, StdDev: 2 |
| **ATR** (Average True Range) | `AtrIndicator` | Volatility-based SL/TP sizing | Period: 14 |
| **Volume Profile** | `VolumeProfileIndicator` | Volume-weighted S/R zones | Lookback: 50 |
| **OBV** (On-Balance Volume) | `ObvIndicator` | Volume trend confirmation | — |

#### Phase 3 — Advanced Confirmation

| Indicator | Class | Purpose | Parameters |
|-----------|-------|---------|------------|
| **Stochastic Oscillator** | `StochasticIndicator` | Momentum reversals | K: 14, D: 3 |
| **ADX** (Average Directional Index) | `AdxIndicator` | Trend strength filter | Period: 14 |
| **Ichimoku Cloud** | `IchimokuIndicator` | Multi-signal confluence | Tenkan: 9, Kijun: 26, Senkou: 52 |
| **VWAP** | `VwapIndicator` | Intraday fair value | Session-based |

### Integration into PriceActionStrategy

The indicators will be integrated as **confirmation layers** within
`PriceActionStrategy.analyze()`:

```
Current flow:
  S/R Detection → Trend Analysis → Pattern Detection → Signal → Confidence

Enhanced flow:
  S/R Detection → Trend Analysis → Pattern Detection
       ↓
  Indicator Confirmations (RSI, MACD, EMA, Bollinger, etc.)
       ↓
  Weighted Signal Aggregation → Adjusted Confidence → Final Signal
```

**Confidence adjustment formula** (planned):

```
confirming_indicators = count of indicators agreeing with signal direction
total_indicators = count of active indicators
indicator_score = confirming_indicators / total_indicators
adjusted_confidence = base_confidence × 0.6 + indicator_score × 0.4
```

### Indicator Configuration in UserPreferences

Each user will be able to enable/disable specific indicators and adjust parameters:

```java
// Planned additions to UserPreferences
Map<String, Boolean> enabledIndicators;     // e.g., {"RSI": true, "MACD": true}
Map<String, Map<String, String>> indicatorParams;  // e.g., {"RSI": {"period": "14"}}
```

---

## Future Plans — Platform Features

### Phase 1 — Core Platform

- [x] REST API controllers for user management, preferences, credential management, analysis triggers
- [ ] Scheduled trading — cron-based analysis runs per user
- [ ] Order execution — place real trades via Coinbase Advanced Trade API
- [ ] WebSocket price feeds — real-time candle updates

### Phase 2 — User Management

- [ ] User registration and authentication (Spring Security + JWT)
- [ ] Role-based access control (ADMIN, TRADER, VIEWER)
- [ ] API rate limiting per user
- [ ] Audit logging for all trade decisions and executions

### Phase 3 — Analytics & Monitoring

- [ ] Trade performance tracking (win rate, P&L, drawdown)
- [ ] Strategy backtesting engine
- [ ] Dashboard UI for monitoring signals and positions
- [ ] Alert notifications (email, webhook, push)

### Phase 4 — Advanced Trading

- [ ] Multiple strategy support per user (portfolio of strategies)
- [ ] Paper trading mode (simulated execution)
- [ ] Multi-exchange support (beyond Coinbase)
- [ ] Machine learning signal enhancement
