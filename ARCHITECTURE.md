# Smart Trader V1 — Architecture Design

## Table of Contents

- [High-Level Architecture](#high-level-architecture)
  - [System Overview](#system-overview)
  - [System Context Diagram](#system-context-diagram)
  - [Major Components](#major-components)
  - [Technology Stack](#technology-stack)
  - [Data Flow Overview](#data-flow-overview)
  - [Deployment Architecture](#deployment-architecture)
  - [Security Architecture](#security-architecture)
- [Low-Level Architecture](#low-level-architecture)
  - [Package Structure](#package-structure)
  - [Component Details](#component-details)
    - [Models Layer](#models-layer)
    - [Strategy Layer](#strategy-layer)
    - [Service Layer](#service-layer)
    - [Configuration Layer](#configuration-layer)
    - [Repository Layer](#repository-layer)
  - [Design Patterns](#design-patterns)
  - [Detailed Data Flows](#detailed-data-flows)
    - [Trading Analysis Flow](#trading-analysis-flow)
    - [Credential Management Flow](#credential-management-flow)
    - [Market Data Retrieval Flow](#market-data-retrieval-flow)
  - [Caching Architecture](#caching-architecture)
  - [Database Schema](#database-schema)
  - [Multi-User Isolation](#multi-user-isolation)

---

# High-Level Architecture

## System Overview

Smart Trader V1 is an automated cryptocurrency trading analysis system built on the
Coinbase Advanced Trade API. The application analyzes price-action candlestick data,
detects technical chart patterns, evaluates trend direction, and generates
**BUY / SELL / HOLD** trading signals with confidence scores and risk-managed
position sizing.

The system is designed as a **multi-user, server-side Spring Boot application**
with per-user credential isolation, encrypted credential storage, and a pluggable
strategy engine.

---

## System Context Diagram

```
                        ┌──────────────────────────┐
                        │      External Users       │
                        │  (Traders / Schedulers)   │
                        └────────────┬─────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────┐
│                       Smart Trader V1 (Spring Boot)               │
│                                                                    │
│   ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐  │
│   │  Credential   │  │    Trading       │  │    Market Data     │  │
│   │  Management   │  │    Analysis      │  │    Service         │  │
│   │  Service      │  │    Engine        │  │                    │  │
│   └──────┬───────┘  └────────┬─────────┘  └────────┬───────────┘  │
│          │                   │                      │              │
│          ▼                   ▼                      ▼              │
│   ┌──────────────────────────────────────────────────────────┐    │
│   │              Internal Service Bus (Spring DI)             │    │
│   └──────────────────────────────────────────────────────────┘    │
│                                                                    │
└──────────┬──────────────────────────────────────┬─────────────────┘
           │                                      │
           ▼                                      ▼
   ┌───────────────┐                    ┌─────────────────────────┐
   │   MongoDB 7   │                    │  Coinbase Advanced      │
   │               │                    │  Trade API              │
   │  • Encrypted  │                    │                         │
   │    Credentials│                    │  • Public Market Data   │
   │  • Orders     │                    │  • Authenticated Trade  │
   └───────────────┘                    └─────────────────────────┘
```

---

## Major Components

| Component | Responsibility |
|-----------|---------------|
| **Trading Analysis Engine** | Orchestrates strategy execution, pattern detection, trend analysis, and risk assessment to produce trade signals |
| **Strategy Engine** | Pluggable analysis algorithms (currently Price Action) that evaluate candlestick data and produce BUY/SELL/HOLD decisions |
| **Market Data Service** | Fetches public market data (products, candles, prices) from the Coinbase API with caching |
| **Credential Management** | Handles per-user Coinbase API credential registration, AES-256-GCM encryption, storage, and retrieval |
| **Client Factory** | Creates and caches per-user authenticated Coinbase API clients with automatic cache invalidation |
| **Risk Manager** | Computes position sizing, stop-loss, take-profit levels, and validates trades against user-defined risk limits |
| **Data Persistence** | MongoDB storage for encrypted credentials and trade order records |

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.4.4 |
| Build Tool | Maven | 3.9 |
| Database | MongoDB | 7 (via Spring Data MongoDB) |
| Caching | Caffeine (via Spring Cache) | Latest |
| Exchange SDK | Coinbase Advanced SDK | 0.1.0 |
| Exchange SDK Core | Coinbase Core Java | 1.0.1 |
| Code Generation | Lombok | 1.18.38 |
| Testing | JUnit 5 + Mockito + AssertJ | Latest |
| Container | Docker (multi-stage) + Docker Compose | — |

---

## Data Flow Overview

```
┌──────────┐      ┌──────────────────┐      ┌─────────────────┐
│ Coinbase  │─────▶│  Market Data     │─────▶│  Candle Data    │
│ API       │      │  Service         │      │  (OHLCV)        │
└──────────┘      └──────────────────┘      └────────┬────────┘
                                                      │
                                                      ▼
                                            ┌─────────────────┐
                                            │  Trading        │
                                            │  Orchestrator   │
                                            └────────┬────────┘
                                                     │
                          ┌──────────────────────────┼──────────────────────────┐
                          │                          │                          │
                          ▼                          ▼                          ▼
                ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
                │  S/R Detector   │      │  Trend Analyzer │      │  Pattern        │
                │                 │      │                 │      │  Detector       │
                └────────┬────────┘      └────────┬────────┘      └────────┬────────┘
                         │                        │                        │
                         └────────────────────────┼────────────────────────┘
                                                  │
                                                  ▼
                                        ┌─────────────────┐
                                        │ Signal          │
                                        │ Determination   │
                                        │ + Confidence    │
                                        └────────┬────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │  Risk Manager   │
                                        │  (if applicable)│
                                        └────────┬────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │  TradeDecision  │
                                        │  BUY/SELL/HOLD  │
                                        │  + confidence   │
                                        │  + reasoning    │
                                        └─────────────────┘
```

---

## Deployment Architecture

```
┌──────────────── Docker Compose ─────────────────┐
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │  app (Spring Boot)                         │  │
│  │  • Image: eclipse-temurin:17-jre           │  │
│  │  • Port: 8080                              │  │
│  │  • Env: SPRING_DATA_MONGODB_URI            │  │
│  │  • Env: CREDENTIAL_ENCRYPTION_KEY          │  │
│  │  • Depends on: mongo (healthy)             │  │
│  └────────────────────────────────────────────┘  │
│                        │                         │
│                        ▼                         │
│  ┌────────────────────────────────────────────┐  │
│  │  mongo (MongoDB 7)                         │  │
│  │  • Port: 27017                             │  │
│  │  • Volume: mongo-data:/data/db             │  │
│  │  • Healthcheck: mongosh ping               │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
└──────────────────────────────────────────────────┘
               │
               ▼
    ┌─────────────────────┐
    │  Coinbase Advanced  │
    │  Trade API          │
    │  (External)         │
    └─────────────────────┘
```

**Build Pipeline**: Multi-stage Docker build
- **Stage 1 (Build)**: Maven 3.9 + Eclipse Temurin 17 — compiles source, packages JAR
- **Stage 2 (Runtime)**: Eclipse Temurin 17 JRE — runs the application JAR

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Security Layers                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. CREDENTIALS AT REST                                         │
│     ┌──────────┐    AES-256-GCM     ┌──────────────────────┐   │
│     │ Raw Creds│ ─────────────────▶  │ Encrypted Blob       │   │
│     │ (memory) │  (random 12B IV)   │ (MongoDB)            │   │
│     └──────────┘                     └──────────────────────┘   │
│                                                                 │
│  2. KEY MANAGEMENT                                              │
│     • 256-bit key from environment variable                     │
│     • Base64-encoded, never hardcoded                           │
│     • Same key required across application restarts             │
│                                                                 │
│  3. USER ISOLATION                                              │
│     • Per-user CoinbaseAdvancedClient instances                 │
│     • Unique encryption IV per credential set                   │
│     • No cross-user data leakage                                │
│     • Cache keyed by userId (max 500 concurrent users)          │
│                                                                 │
│  4. TAMPER DETECTION                                            │
│     • GCM 128-bit authentication tag on every ciphertext        │
│     • Decryption fails if ciphertext modified                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

# Low-Level Architecture

## Package Structure

```
com.techcobber.smarttrader.v1
│
├── SmartTraderV1Application.java          # @SpringBootApplication entry point
│
├── config/
│   └── AppConfig.java                     # @EnableCaching, Caffeine bean definitions
│
├── models/
│   ├── MyCandle.java                      # OHLCV candle with Builder + auto pattern detection
│   ├── TradeDecision.java                 # BUY/SELL/HOLD signal output (Lombok @Builder)
│   ├── UserPreferences.java              # Per-user trading configuration
│   ├── UserCredentials.java              # MongoDB @Document — encrypted credentials
│   ├── Order.java                         # MongoDB @Document — trade execution records
│   └── ListCandles.java                   # JSON deserialization wrapper for candle lists
│
├── repositories/
│   └── UserCredentialsRepository.java     # Spring Data MongoDB repository interface
│
├── services/
│   ├── ClientService.java                 # Façade over CoinbaseClientFactory
│   ├── CoinbaseClientFactory.java        # Per-user client creation + Caffeine cache
│   ├── CredentialEncryptionService.java  # AES-256-GCM encrypt/decrypt
│   ├── CoinbasePublicServiceImpl.java    # Market data (extends SDK PublicServiceImpl)
│   └── TradingOrchestrator.java          # Analysis orchestration + risk management
│
└── strategy/
    ├── TradingStrategy.java               # Strategy interface (analyze, getName)
    ├── PriceActionStrategy.java          # Primary strategy implementation
    ├── TrendAnalyzer.java                 # Swing-high/low trend detection
    ├── CandlePatternDetector.java        # 22 candlestick pattern types
    ├── SupportResistanceDetector.java    # S/R level clustering
    └── RiskManager.java                   # Position sizing, SL/TP, R:R validation
```

---

## Component Details

### Models Layer

#### MyCandle

| Field | Type | Description |
|-------|------|-------------|
| `start` | `Long` | Unix timestamp of candle open |
| `open` | `Double` | Opening price |
| `high` | `Double` | Highest price in period |
| `low` | `Double` | Lowest price in period |
| `close` | `Double` | Closing price |
| `volume` | `Double` | Trading volume |
| `color` | `String` | `"GREEN"`, `"RED"`, or `"NEUTRAL"` (computed) |
| `bodySize` | `Double` | `|close - open|` (computed) |
| `upperWickPercent` | `Double` | Upper wick as percentage of total range (computed) |
| `lowerWickPercent` | `Double` | Lower wick as percentage of total range (computed) |
| `candleTypes` | `List<CandleType>` | Detected single-candle patterns (computed) |

**Design Pattern**: Custom Builder with `computeFields()` auto-invoked on `build()`.
Static factory `from(Candle)` converts Coinbase SDK candle objects.

**CandleType Enum** (22 types): `HAMMER`, `INVERTED_HAMMER`, `SHOOTING_STAR`,
`HANGING_MAN`, `DOJI`, `DRAGONFLY_DOJI`, `GRAVESTONE_DOJI`, `SPINNING_TOP`,
`MARUBOZU_BULLISH`, `MARUBOZU_BEARISH`, `BULLISH_ENGULFING`, `BEARISH_ENGULFING`,
`BULLISH_HARAMI`, `BEARISH_HARAMI`, `PIERCING_LINE`, `DARK_CLOUD_COVER`,
`TWEEZER_BOTTOM`, `TWEEZER_TOP`, `MORNING_STAR`, `EVENING_STAR`,
`THREE_WHITE_SOLDIERS`, `THREE_BLACK_CROWS`

#### TradeDecision

| Field | Type | Description |
|-------|------|-------------|
| `signal` | `Signal` | `BUY`, `SELL`, or `HOLD` |
| `confidence` | `Double` | 0.0–1.0 confidence score |
| `reasoning` | `String` | Human-readable explanation |
| `detectedPatterns` | `List<String>` | Names of detected patterns |
| `trendDirection` | `String` | `"UP"`, `"DOWN"`, or `"SIDEWAYS"` |
| `nearestSupport` | `Double` | Nearest support price level |
| `nearestResistance` | `Double` | Nearest resistance price level |
| `productId` | `String` | Trading pair (e.g., `"BTC-USD"`) |
| `timestamp` | `LocalDateTime` | UTC timestamp of decision |

**Design Pattern**: Lombok `@Builder` for immutable construction.

#### UserCredentials (MongoDB Document)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | MongoDB document `_id` |
| `userId` | `String` | User identifier (unique index) |
| `encryptedCredentials` | `String` | AES-256-GCM encrypted blob (Base64) |
| `createdAt` | `Instant` | Creation timestamp |
| `updatedAt` | `Instant` | Last update timestamp |

#### UserPreferences

| Field | Type | Description |
|-------|------|-------------|
| `userId` | `String` | User identifier |
| `strategy` | `String` | Strategy name (e.g., `"PriceAction"`) |
| `granularity` | `String` | Candle timeframe (e.g., `"ONE_HOUR"`) |
| `baseAsset` / `quoteAsset` | `String` | Trading pair components |
| `positionSize` | `String` | Position size percentage |
| `maxDailyLoss` | `String` | Maximum daily loss threshold |
| `timezone` | `String` | User's timezone |
| `enabled` | `boolean` | Whether trading is active |

#### Order (MongoDB Document)

| Field | Type | Description |
|-------|------|-------------|
| `userId` | `String` | User identifier |
| `orderTS` | `LocalDateTime` | Execution timestamp |
| `productId` | `String` | Trading pair |
| `price` / `qty` | `Double` | Execution price and quantity |
| `side` | `String` | `"BUY"` or `"SELL"` |
| `decisionFactors` | `Map<String, String>` | Patterns, trend, confidence context |
| `comments` | `String` | Additional notes |

#### ListCandles

Wrapper class with a single `candles: List<MyCandle>` field for JSON deserialization
of Coinbase candle API responses.

---

### Strategy Layer

#### TradingStrategy Interface

```java
public interface TradingStrategy {
    TradeDecision analyze(List<MyCandle> candles);
    String getName();
}
```

All trading strategies implement this contract. The orchestrator uses the interface,
enabling strategy swapping without code changes.

#### PriceActionStrategy

The primary strategy implementation. Requires a minimum of **20 candles** to operate
(returns HOLD with zero confidence if fewer).

**Analysis Pipeline** (6 sequential steps):

```
Step 1: S/R Detection
  └─ SupportResistanceDetector.detectLevels(candles, lookback=50)
  └─ Find nearest support (below price) and resistance (above price)

Step 2: Trend Analysis
  └─ TrendAnalyzer.analyzeTrend(candles, lookback=20)
  └─ Returns direction (UP/DOWN/SIDEWAYS) + strength (0.0–1.0)

Step 3: Pattern Detection
  └─ CandlePatternDetector.detectPatterns(candles)
  └─ Scans 1/2/3-candle windows, counts bullish vs bearish biases

Step 4: Signal Determination (first match wins)
  ├─ Breakout above resistance + Uptrend + Bullish patterns    → BUY
  ├─ Breakdown below support + Downtrend + Bearish patterns    → SELL
  ├─ Bounce at support + Bullish patterns + Non-bearish trend  → BUY
  ├─ Rejection at resistance + Bearish + Non-bullish trend     → SELL
  ├─ ≥2 bullish patterns + Uptrend                             → BUY
  ├─ ≥2 bearish patterns + Downtrend                           → SELL
  └─ Otherwise                                                  → HOLD

Step 5: Confidence Calculation
  └─ Base: 0.3
  └─ + Trend alignment bonus:   0.2 × trend_strength
  └─ + Pattern strength bonus:  0.08 per pattern (capped at 0.25)
  └─ + Strong pattern bonus:    0.1 (ENGULFING, MORNING_STAR, etc.)
  └─ + S/R proximity bonus:     0.15 (within 2%) or 0.05 (within 5%)
  └─ Final: capped at 1.0, rounded to 2 decimals

Step 6: Reasoning Generation
  └─ Human-readable string: signal, trend, patterns, S/R levels, price
```

#### TrendAnalyzer

Detects market trend direction and strength from swing highs/lows.

- **Input**: `List<MyCandle>` candles, `int lookback`
- **Output**: `TrendResult(direction, strength, description)`
  - `direction`: `UP` | `DOWN` | `SIDEWAYS`
  - `strength`: 0.0–1.0

**Algorithm**:
1. Extract swing highs and swing lows from recent candles
2. Count higher-highs + higher-lows → `bullishSignals`
3. Count lower-highs + lower-lows → `bearishSignals`
4. If bullish > bearish → UP, strength = bullish / total
5. If bearish > bullish → DOWN, strength = bearish / total
6. Otherwise → SIDEWAYS

#### CandlePatternDetector

Identifies 22 candlestick pattern types across three detection windows.

| Window | Scan Range | Patterns |
|--------|-----------|----------|
| Single candle | Last 5 candles | Hammer, Inverted Hammer, Shooting Star, Hanging Man, Doji (standard, Dragonfly, Gravestone), Spinning Top, Marubozu (Bullish/Bearish) |
| Two candles | Last 4 pairs | Bullish/Bearish Engulfing, Bullish/Bearish Harami, Piercing Line, Dark Cloud Cover, Tweezer Bottom/Top |
| Three candles | Last 5 triplets | Morning Star, Evening Star, Three White Soldiers, Three Black Crows |

- **Output**: `List<DetectedPattern>` where each contains `name`, `bias` (BULLISH/BEARISH/NEUTRAL), and `candleIndex`

#### SupportResistanceDetector

Identifies support and resistance price levels through swing point clustering.

- **Input**: `List<MyCandle>` candles, `int lookback`
- **Output**: `List<Level>` sorted by strength (descending)

**Algorithm**:
1. Extract swing highs (local maxima → resistance) and swing lows (local minima → support)
2. Cluster levels within **0.5% tolerance** of each other
3. Average clustered prices
4. Compute strength = `max(touches, cluster_size)`
5. Sort by strength descending

#### RiskManager

Computes position sizing and validates trades against risk limits.

- **Input**: `TradeDecision`, `UserPreferences`, `currentPrice`, `accountBalance`
- **Output**: `RiskAssessment`

| Field | Computation |
|-------|------------|
| `positionSize` | `accountBalance × (positionSize% / 100) / currentPrice` |
| `stopLoss` | BUY: `nearestSupport × 0.995` (or `price × 0.98`); SELL: `nearestResistance × 1.005` (or `price × 1.02`) |
| `takeProfit` | `currentPrice ± (riskPerUnit × 2.0)` — 1:2 risk-reward ratio |
| `riskRewardRatio` | `rewardPerUnit / riskPerUnit` |
| `approved` | `true` if potential loss ≤ `maxDailyLoss`; `false` otherwise |

---

### Service Layer

#### TradingOrchestrator

Central orchestration service (`@Service`). Coordinates strategy execution with
optional risk management.

| Method | Signature | Description |
|--------|-----------|-------------|
| `executeAnalysis` | `(List<MyCandle>, String productId) → TradeDecision` | Runs PriceActionStrategy on candles, logs result, returns decision |
| `executeWithRisk` | `(List<MyCandle>, String productId, UserPreferences, double balance) → Map<String, Object>` | Runs analysis; if signal ≠ HOLD and confidence ≥ 0.6, adds RiskManager assessment |

#### ClientService

Façade that delegates to `CoinbaseClientFactory` providing a simplified interface.

| Method | Description |
|--------|-------------|
| `getClientForUser(userId)` | Returns per-user authenticated `CoinbaseAdvancedClient` |
| `registerCredentials(userId, rawCreds)` | Encrypts and stores credentials in MongoDB |
| `removeCredentials(userId)` | Deletes credentials and evicts cached client |
| `hasCredentials(userId)` | Checks if user has stored credentials |

#### CoinbaseClientFactory

Factory + Cache-Aside pattern for per-user client lifecycle management.

| Aspect | Detail |
|--------|--------|
| **Cache implementation** | Caffeine (manual, not Spring-managed) |
| **Cache TTL** | 30 minutes (expireAfterWrite) |
| **Cache max size** | 500 entries |
| **Cache key** | `userId` |
| **Cache value** | `CoinbaseAdvancedClient` instance |
| **Invalidation** | Automatic on `registerCredentials()` or `removeCredentials()` |

**Client build process** (on cache miss):
1. `UserCredentialsRepository.findByUserId(userId)` — fetch encrypted blob
2. `CredentialEncryptionService.decrypt(encryptedBlob)` — decrypt to plaintext
3. `new CoinbaseAdvancedCredentials(plaintext)` — parse credentials
4. `new CoinbaseAdvancedClient(credentials)` — create authenticated client
5. Cache the client (30 min TTL)

#### CredentialEncryptionService

Handles symmetric encryption/decryption of Coinbase API credentials.

| Aspect | Detail |
|--------|--------|
| **Algorithm** | AES/GCM/NoPadding |
| **Key size** | 256 bits (32 bytes) |
| **IV (nonce)** | 12 bytes, randomly generated per encryption |
| **GCM tag length** | 128 bits |
| **Key source** | `CREDENTIAL_ENCRYPTION_KEY` environment variable (Base64-encoded) |
| **Storage format** | `Base64(IV ‖ ciphertext ‖ GCM_tag)` |

**Encrypt**: Generate random IV → AES-GCM encrypt → prepend IV → Base64 encode
**Decrypt**: Base64 decode → extract 12-byte IV → AES-GCM decrypt → return plaintext

#### CoinbasePublicServiceImpl

Extends the Coinbase SDK `PublicServiceImpl` (Template Method pattern) to provide
enriched market data methods.

| Method | Description |
|--------|-------------|
| `fetchPublicProduct(productId)` | Product details for a single trading pair |
| `fetchCandles(productId, start, end, granularity)` | OHLCV candle data for a time range |
| `getPrice(productId)` | Current price of a product |
| `getFilteredProducts(filter)` | Filter products by ID substring (e.g., `"USDC"`) |
| `getTopVolumeProductsInLast24Hrs(limit)` | Products sorted by 24h volume (descending) |
| `getTopVolumeChangeProductsInLast24Hrs(limit, filter)` | Sorted by 24h volume change % |
| `getBottomVolumeChangeProductsInLast24Hrs(limit, filter)` | Lowest volume movers |
| `getTopPriceChangeProductsInLast24Hrs(limit, filter)` | Highest price gainers |
| `getBottomPriceChangeProductsInLast24Hrs(limit, filter)` | Biggest price losers |
| `listPublicProducts()` | All products — **cached via `@Cacheable("publicProducts")`** |

---

### Configuration Layer

#### AppConfig

Spring `@Configuration` class with `@EnableCaching`.

| Bean | Configuration | Purpose |
|------|--------------|---------|
| `caffeineConfig()` | 5-minute TTL, 1000-entry max | Caffeine cache builder for Spring Cache |
| `cacheManager(Caffeine)` | `CaffeineCacheManager` | Integrates Caffeine with Spring `@Cacheable` |

#### application.properties

| Property | Value | Purpose |
|----------|-------|---------|
| `spring.application.name` | `smart-trader-v1` | Application name |
| `spring.data.mongodb.host` | `localhost` | MongoDB host |
| `spring.data.mongodb.port` | `27017` | MongoDB port |
| `spring.data.mongodb.database` | `coinbase` | Database name |
| `PUBLIC_API_URL` | `https://api.coinbase.com/api/v3/brokerage/market/products` | Coinbase public API base URL |
| `logging.file.name` | `logs/smart_trader_v1.log` | Log file path |

---

### Repository Layer

#### UserCredentialsRepository

Spring Data MongoDB repository interface for the `user_credentials` collection.

| Method | Description |
|--------|-------------|
| `findByUserId(String userId)` | Fetch credentials by user ID |
| `existsByUserId(String userId)` | Check existence by user ID |
| `deleteByUserId(String userId)` | Remove credentials by user ID |

---

## Design Patterns

| Pattern | Component | Purpose |
|---------|-----------|---------|
| **Strategy** | `TradingStrategy` interface + `PriceActionStrategy` | Pluggable, interchangeable trading analysis algorithms |
| **Builder** | `MyCandle.Builder` (custom) | Fluent construction with auto-computed derived fields |
| **Builder** | `TradeDecision` (Lombok `@Builder`) | Immutable signal object construction |
| **Template Method** | `CoinbasePublicServiceImpl extends PublicServiceImpl` | Reuse SDK HTTP infrastructure while adding custom market data methods |
| **Factory + Cache-Aside** | `CoinbaseClientFactory` | Per-user client creation with cache-first lookup |
| **Façade** | `ClientService` | Simplified API abstracting factory internals |
| **Factory Method** | `AppConfig @Bean` methods | Spring-managed bean creation for cache infrastructure |
| **Orchestrator** | `TradingOrchestrator` | Coordinates multi-step analysis pipeline across strategy and risk components |

---

## Detailed Data Flows

### Trading Analysis Flow

```
1. INITIATION
   Caller invokes TradingOrchestrator.executeAnalysis(candles, productId)
                                        │
                                        ▼
2. STRATEGY DISPATCH
   TradingOrchestrator delegates to PriceActionStrategy.analyze(candles)
                                        │
        ┌───────────────────────────────┼──────────────────────────────┐
        ▼                               ▼                              ▼
3a. S/R DETECTION                 3b. TREND ANALYSIS           3c. PATTERN DETECTION
    SupportResistanceDetector         TrendAnalyzer                CandlePatternDetector
    .detectLevels(candles, 50)        .analyzeTrend(candles, 20)   .detectPatterns(candles)
    │                                 │                             │
    ├─ Swing highs → resistance       ├─ Count HH, HL, LH, LL     ├─ Scan last 5 singles
    ├─ Swing lows → support           ├─ Compare bullish vs bear   ├─ Scan last 4 pairs
    ├─ Cluster within 0.5%            └─ Return TrendResult        ├─ Scan last 5 triplets
    └─ Return List<Level>                                          └─ Return List<DetectedPattern>
        │                               │                              │
        └───────────────────────────────┼──────────────────────────────┘
                                        │
                                        ▼
4. SIGNAL DETERMINATION
   Apply priority rules (breakout, bounce, pattern count) → BUY / SELL / HOLD
                                        │
                                        ▼
5. CONFIDENCE SCORING
   base(0.3) + trend_bonus + pattern_bonus + S/R_proximity → [0.0, 1.0]
                                        │
                                        ▼
6. REASONING GENERATION
   Build human-readable explanation string
                                        │
                                        ▼
7. RETURN TradeDecision
   { signal, confidence, reasoning, patterns, trend, S/R, productId, timestamp }
```

**Optional Risk Extension** (`executeWithRisk`):

```
8. RISK GATE (confidence ≥ 0.6 AND signal ≠ HOLD)
                                        │
                                        ▼
9. RISK ASSESSMENT
   RiskManager.assess(decision, prefs, currentPrice, accountBalance)
   │
   ├─ Position size = balance × pct / price
   ├─ Stop-loss = nearest S/R ± buffer (or default ±2%)
   ├─ Take-profit = price ± (risk × 2.0)
   ├─ Validate: potential loss ≤ maxDailyLoss
   └─ Return RiskAssessment { positionSize, SL, TP, R:R, approved, reason }
```

---

### Credential Management Flow

```
REGISTRATION
═══════════════════════════════════════════════════════════

1. ClientService.registerCredentials(userId, rawCredentials)
           │
           ▼
2. CoinbaseClientFactory.registerCredentials(userId, rawCredentials)
           │
           ▼
3. CredentialEncryptionService.encrypt(rawCredentials)
   ├─ Generate random 12-byte IV
   ├─ AES-256-GCM encrypt with IV + master key
   └─ Return Base64(IV ‖ ciphertext ‖ GCM_tag)
           │
           ▼
4. UserCredentialsRepository.save(UserCredentials{userId, encryptedBlob})
   └─ MongoDB persists to user_credentials collection
           │
           ▼
5. CoinbaseClientFactory.clientCache.invalidate(userId)
   └─ Evict any stale cached client for this user


RETRIEVAL
═══════════════════════════════════════════════════════════

1. ClientService.getClientForUser(userId)
           │
           ▼
2. CoinbaseClientFactory.clientCache.get(userId, buildClientFn)
           │
     ┌─────┴─────┐
     ▼           ▼
  CACHE HIT   CACHE MISS
  Return       │
  immediately  ▼
            3. UserCredentialsRepository.findByUserId(userId)
               └─ Fetch encrypted blob from MongoDB
                          │
                          ▼
            4. CredentialEncryptionService.decrypt(encryptedBlob)
               ├─ Base64 decode
               ├─ Extract 12-byte IV
               ├─ AES-256-GCM decrypt
               └─ Return plaintext credentials
                          │
                          ▼
            5. new CoinbaseAdvancedCredentials(plaintext)
               └─ Parse into SDK credential object
                          │
                          ▼
            6. new CoinbaseAdvancedClient(credentials)
               └─ Create authenticated API client
                          │
                          ▼
            7. Cache client (TTL 30 min, max 500 users)
               └─ Return client to caller
```

---

### Market Data Retrieval Flow

```
1. Caller requests market data (candles, prices, product lists)
                    │
                    ▼
2. CoinbasePublicServiceImpl
   ├─ Inherits HTTP infrastructure from SDK PublicServiceImpl
   ├─ No authentication required for public endpoints
   │
   ├── listPublicProducts()
   │   └─ @Cacheable("publicProducts") → Caffeine (5 min TTL)
   │   └─ On cache miss: HTTP GET to Coinbase public products API
   │
   ├── fetchCandles(productId, start, end, granularity)
   │   └─ Direct HTTP call (no caching — time-sensitive data)
   │
   ├── getPrice(productId)
   │   └─ Fetches product → extracts current price
   │
   └── Filtered/sorted product queries
       └─ Call listPublicProducts() (cached) → filter/sort in memory
```

---

## Caching Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     Caching Layers                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  LAYER 1: Spring Cache (via AppConfig)                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Cache Name: "publicProducts"                          │  │
│  │  Backend: Caffeine                                     │  │
│  │  TTL: 5 minutes (expireAfterWrite)                     │  │
│  │  Max Size: 1000 entries                                │  │
│  │  Used By: CoinbasePublicServiceImpl.listPublicProducts │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  LAYER 2: Custom Caffeine Cache (CoinbaseClientFactory)      │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Cache Name: clientCache (manual)                      │  │
│  │  Backend: Caffeine (not Spring-managed)                │  │
│  │  TTL: 30 minutes (expireAfterWrite)                    │  │
│  │  Max Size: 500 entries                                 │  │
│  │  Key: userId → Value: CoinbaseAdvancedClient           │  │
│  │  Invalidation: On credential register/remove           │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Database Schema

### MongoDB Collections

```
┌─────────────────────────────────────────────────────┐
│  Database: coinbase                                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Collection: user_credentials                       │
│  ┌─────────────────────────────────────────────┐    │
│  │  _id: ObjectId (auto)                       │    │
│  │  userId: String (unique index)              │    │
│  │  encryptedCredentials: String (Base64 blob) │    │
│  │  createdAt: ISODate                         │    │
│  │  updatedAt: ISODate                         │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  Collection: orders                                 │
│  ┌─────────────────────────────────────────────┐    │
│  │  _id: ObjectId (auto)                       │    │
│  │  userId: String                             │    │
│  │  orderTS: ISODate                           │    │
│  │  productId: String                          │    │
│  │  price: Double                              │    │
│  │  qty: Double                                │    │
│  │  side: String ("BUY" / "SELL")              │    │
│  │  decisionFactors: Object (key-value map)    │    │
│  │  comments: String                           │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## Multi-User Isolation

The application isolates user data and API access at multiple levels:

```
┌───────────────────────────────────────────────────────────────────────┐
│                     Multi-User Isolation Model                       │
├───────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  User A                                  User B                       │
│  ┌──────────────────────┐                ┌──────────────────────┐     │
│  │ Credentials (A)      │                │ Credentials (B)      │     │
│  │ • Unique encryption  │                │ • Unique encryption  │     │
│  │   IV per set         │                │   IV per set         │     │
│  │ • Stored separately  │                │ • Stored separately  │     │
│  │   in MongoDB         │                │   in MongoDB         │     │
│  └──────────┬───────────┘                └──────────┬───────────┘     │
│             │                                       │                 │
│             ▼                                       ▼                 │
│  ┌──────────────────────┐                ┌──────────────────────┐     │
│  │ CoinbaseClient (A)   │                │ CoinbaseClient (B)   │     │
│  │ • Separate instance  │                │ • Separate instance  │     │
│  │ • Cached 30 min      │                │ • Cached 30 min      │     │
│  │ • Isolated API scope │                │ • Isolated API scope │     │
│  └──────────────────────┘                └──────────────────────┘     │
│                                                                       │
│  Guarantees:                                                          │
│  • No cross-user credential access                                    │
│  • No shared API client state                                         │
│  • Independent cache entries per user                                 │
│  • Credential update for one user does not affect another             │
│  • Max 500 concurrent cached users                                    │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```
