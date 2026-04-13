# Smart Trader UI

A modern React + TypeScript frontend for the Smart Trader trading application. Built with Vite, TailwindCSS, and industry-standard tooling.

## Tech Stack

- **Framework**: React 19 with TypeScript
- **Build Tool**: Vite 8
- **Styling**: TailwindCSS 4
- **HTTP Client**: Axios with JWT interceptors
- **Routing**: React Router 7
- **Testing**: Vitest + React Testing Library
- **Linting**: ESLint + Prettier
- **Deployment**: Docker (nginx) + AWS ECS Fargate

## Getting Started

### Prerequisites

- Node.js 22+
- npm 10+

### Installation

```bash
npm install
```

### Development

```bash
# Start development server (proxies API to localhost:8080)
npm run dev

# The app will be available at http://localhost:3000
```

### Environment Variables

Copy `.env.example` to `.env.local` and configure:

```env
VITE_API_BASE_URL=http://localhost:8080
```

### Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server |
| `npm run build` | Build for production |
| `npm run preview` | Preview production build |
| `npm run test` | Run tests |
| `npm run test:watch` | Run tests in watch mode |
| `npm run test:coverage` | Run tests with coverage |
| `npm run lint` | Run ESLint |
| `npm run lint:fix` | Fix ESLint issues |
| `npm run format` | Format code with Prettier |
| `npm run format:check` | Check formatting |

## Project Structure

```
src/
├── api/              # API client layer (Axios + typed endpoints)
├── components/       # React components
│   ├── common/       # Reusable UI components (Button, Card, Alert, etc.)
│   ├── layout/       # Layout components (Header, Sidebar, Layout)
│   ├── dashboard/    # Dashboard widgets
│   ├── orders/       # Order management (OrderForm, OrderList)
│   ├── scanner/      # Market scanner (ScanResults, ProductAnalysis)
│   ├── credentials/  # Credential management
│   └── preferences/  # Trading preferences
├── context/          # React context (AuthContext)
├── hooks/            # Custom hooks (useAuth, useApi)
├── pages/            # Page components (routed views)
├── types/            # TypeScript type definitions
├── utils/            # Utility functions (formatters, validators, constants)
├── styles/           # Global styles (TailwindCSS)
└── __tests__/        # Test files
```

## Architecture

### API Layer
All backend communication goes through typed API modules in `src/api/`. The `apiClient.ts` configures Axios with:
- JWT Bearer token injection via request interceptors
- Automatic 401 handling with session cleanup
- 30-second request timeout

### Authentication
- JWT tokens stored in `sessionStorage` (cleared on tab close)
- `AuthContext` provides authentication state to all components
- `ProtectedRoute` wrapper redirects unauthenticated users to login
- Automatic logout on 401 responses from the API

### Security
- XSS prevention via input sanitization (`sanitizeInput()`)
- No `localStorage` for tokens (sessionStorage only)
- CSP headers configured in nginx
- Non-root Docker container
- Environment variables for all secrets/configuration

## Deployment

### Docker

```bash
docker build -t smart-trader-ui .
docker run -p 80:80 smart-trader-ui
```

### AWS ECS Fargate

The `aws/task-definition.json` provides an ECS task definition. Replace placeholder values:
- `ACCOUNT_ID` — your AWS account ID
- `REGION` — your AWS region

## Backend API

This UI connects to the Smart Trader backend API with the following endpoints:

| Endpoint | Description |
|----------|-------------|
| `POST /api/users` | Create user |
| `GET /api/users/{userId}` | Get user |
| `POST /api/credentials/{userId}` | Register Coinbase credentials |
| `POST /api/orders/{userId}` | Place order |
| `GET /api/orders/user/{userId}` | List user orders |
| `POST /api/scanner/scan` | Run market scan |
| `GET /api/scanner/results` | Get cached scan results |
| `GET /api/scanner/analyze/{productId}` | Analyze product |
| `PUT /api/users/{userId}/preferences` | Save preferences |
