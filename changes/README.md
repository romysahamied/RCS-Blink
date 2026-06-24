# Platform integration (reference only)

This folder holds the **external platform API** work that was reverted from `main`.
It is **not loaded** by the running app — copy into `api/src/` when you want to enable it.

**IDE note:** `changes/tsconfig.json` + a `changes/node_modules` junction to `api/node_modules` let TypeScript resolve `@nestjs/mongoose` and other packages. If imports stay red, run **TypeScript: Restart TS Server** from the command palette. When you copy the module into `api/src/platform-integration/`, change imports back to relative paths (`../gateway/...`) as in `wiring/`.

## Module (`platform-integration/`)

Mirror of `api/src/platform-integration/`:

- `POST /api/v1/platform/messages` — default channel RCS
- `POST /api/v1/platform/rcs/messages` — always RCS
- Body: `apiToken`, `messageType`, `messageEncoding`, `destinationAddress`, `sourceAddress`, `messageText`, `callBackUrl`, `userReferenceId`
- DLR callbacks POST to `callBackUrl` on SMS status updates

## Wiring (`wiring/`)

Files from commit `59307c8` showing how to register the module:

| File | Apply to |
|------|----------|
| `wiring/app.module.ts` | `api/src/app.module.ts` (add `PlatformIntegrationModule`) |
| `wiring/google-rbm-webhook.service.ts` | `api/src/gateway/transport/google-rbm-webhook.service.ts` |
| `wiring/gateway.service.spec.ts` | `api/src/gateway/gateway.service.spec.ts` |
| `wiring/gateway.service.ts.snippet-reference.txt` | `api/src/gateway/gateway.service.ts` (EventEmitter + `sms.status.updated`) |
| `wiring/env.example.additions.txt` | merge `PLATFORM_*` lines into `api/.env.example` |

## Quick enable

1. Copy `changes/platform-integration/` → `api/src/platform-integration/`
2. Merge wiring files into the paths above
3. Restart API
