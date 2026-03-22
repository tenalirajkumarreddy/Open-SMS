# Workspace

## Overview

OpenSMS Dashboard — a web companion app for the OpenSMS Android gateway (APK). The dashboard connects to your Android phone running the OpenSMS APK via its HTTP API, and provides a web interface to send SMS, manage templates, view logs, and monitor gateway status.

pnpm workspace monorepo using TypeScript. Each package manages its own dependencies.

## Stack

- **Monorepo tool**: pnpm workspaces
- **Node.js version**: 24
- **Package manager**: pnpm
- **TypeScript version**: 5.9
- **API framework**: Express 5
- **Database**: PostgreSQL + Drizzle ORM
- **Validation**: Zod (`zod/v4`), `drizzle-zod`
- **API codegen**: Orval (from OpenAPI spec)
- **Build**: esbuild (CJS bundle)
- **Frontend**: React + Vite, Tailwind CSS, shadcn/ui, React Query

## Structure

```text
artifacts-monorepo/
├── artifacts/              # Deployable applications
│   ├── api-server/         # Express API server
│   └── opensms-dashboard/  # React frontend dashboard
├── lib/                    # Shared libraries
│   ├── api-spec/           # OpenAPI spec + Orval codegen config
│   ├── api-client-react/   # Generated React Query hooks
│   ├── api-zod/            # Generated Zod schemas from OpenAPI
│   └── db/                 # Drizzle ORM schema + DB connection
├── scripts/                # Utility scripts (single workspace package)
├── pnpm-workspace.yaml     # pnpm workspace
├── tsconfig.base.json      # Shared TS options
├── tsconfig.json           # Root TS project references
└── package.json            # Root package with hoisted devDeps
```

## OpenSMS Dashboard Features

1. **Dashboard** — Gateway connection status (running/offline), uptime, queue depth, stats (sent today/week, failed, pending), recent activity feed
2. **Send SMS** — Form to send SMS via the gateway; supports raw body or template-based messages with variable filling
3. **Templates** — Full CRUD for SMS templates; default templates: otp, welcome, order_placed, payment, alert; template body uses `{{var}}` syntax
4. **Logs** — Filterable message log table (All/Delivered/Sent/Failed/Pending); persisted to PostgreSQL
5. **Settings** — Gateway URL + API key configuration, test connection, SMS rate limit, webhook URL

## Database Schema

- `gateway_config` — stores the phone gateway URL + API key
- `templates` — SMS templates with name + body (seeded with 5 defaults on first request)
- `message_logs` — log of all sent messages with status tracking
- `settings` — app settings (auto-start, notify-on-failure, rate limit, webhook URL)

## API Routes (`/api`)

- `GET /api/gateway/config` / `PUT /api/gateway/config` — gateway configuration
- `GET /api/gateway/health` — proxy to phone gateway's `/health` endpoint
- `POST /api/messages/send` — proxy SMS send via phone gateway
- `GET /api/messages/:messageId/status` — message delivery status
- `GET/POST /api/templates` — list/create templates
- `GET/PUT/DELETE /api/templates/:name` — template CRUD
- `GET /api/logs` — message logs with optional status filter
- `GET /api/logs/stats` — aggregate statistics
- `GET/PUT /api/settings` — settings

## Packages

### `artifacts/api-server` (`@workspace/api-server`)

Express 5 API server. Routes live in `src/routes/` and use `@workspace/api-zod` for request and response validation and `@workspace/db` for persistence.

- Entry: `src/index.ts` — reads `PORT`, starts Express
- App setup: `src/app.ts` — mounts CORS, JSON/urlencoded parsing, routes at `/api`
- Routes: `src/routes/index.ts` mounts sub-routers
- `src/lib/gatewayClient.ts` — HTTP client for the Android gateway
- Depends on: `@workspace/db`, `@workspace/api-zod`

### `artifacts/opensms-dashboard` (`@workspace/opensms-dashboard`)

React + Vite frontend. Uses generated React Query hooks from `@workspace/api-client-react`.

### `lib/db` (`@workspace/db`)

Database layer using Drizzle ORM with PostgreSQL.

- `src/schema/gatewayConfig.ts` — gateway config table
- `src/schema/templates.ts` — templates table
- `src/schema/messageLogs.ts` — message log table
- `src/schema/settings.ts` — settings table

### `lib/api-spec` (`@workspace/api-spec`)

OpenAPI 3.1 spec and Orval config. Run codegen: `pnpm --filter @workspace/api-spec run codegen`

### `lib/api-zod` (`@workspace/api-zod`)

Generated Zod schemas from the OpenAPI spec.

### `lib/api-client-react` (`@workspace/api-client-react`)

Generated React Query hooks and fetch client from the OpenAPI spec.
