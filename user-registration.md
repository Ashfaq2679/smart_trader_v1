# SmartTrader — User Registration & Credential Management Guide

## Overview

SmartTrader requires Coinbase Advanced Trade API credentials to interact with
the exchange on your behalf. This guide walks you through registering a user
and securely storing their credentials.

---

## Prerequisites

- SmartTrader application running (default: `http://localhost:8080`)
- A valid Coinbase Advanced Trade API key (see [Step-by-Step Testing Guide](TESTING_GUIDE.md))
- `CREDENTIAL_ENCRYPTION_KEY` configured in the application environment

---

## API Endpoints

| Action | Method | Endpoint |
|---|---|---|
| Register / Update Credentials | `POST` | `/api/credentials/register` |
| Check Credentials Exist | `GET` | `/api/credentials/{userId}/exists` |
| Remove Credentials | `DELETE` | `/api/credentials/{userId}` |

> Adjust base path to match your actual controller mappings.

---

## Step 1 — Obtain Coinbase API Credentials

1. Log in to [Coinbase](https://www.coinbase.com)
2. Navigate to **Settings → API**
3. Click **New API Key**
4. Select permissions:
   - ✅ **View** — required for reading market data and account info
   - ✅ **Trade** — required for placing and cancelling orders
   - ❌ **Transfer** — **keep disabled** for safety
5. Complete 2FA verification
6. Copy the **API Key Name** and **Private Key** (shown only once)

---

## Step 2 — Register Credentials

### Using cURL (Windows CMD)

