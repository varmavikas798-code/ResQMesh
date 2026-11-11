# ResQMesh — Project Status & Context Notes

> Handoff document for any AI session / developer continuing this work.
> Last updated: Nov 11, 2026

## What ResQMesh is
Emergency SOS app with 3 communication layers:
1. **Internet:** SOS → FastAPI server → PostGIS finds responders within 3km → Firebase push notifications
2. **No internet, has signal:** Android app sends SMS directly to responder phones
3. **No internet/signal:** BLE mesh — SOS hops phone-to-phone, uploads when a phone gets internet

## LIVE deployed system (working, tested end-to-end)
- **Backend (FastAPI + PostGIS):** https://resqmesh-api-3g1c.onrender.com
  - Hosted on **Render** free tier (SLEEPS after 15 min idle — cold start 30-60s!)
  - Docs at `/docs`, health at `/health`
- **Dashboard (Next.js):** https://reshqmesh-six.vercel.app (hosted on Vercel, free)
- **Database:** Render PostgreSQL + PostGIS (`resqmesh_db`, internal URL in Render env vars)
- **GitHub:** https://github.com/varmavikas798-code/ResQMesh (all code synced)

## Architecture
- `backend/app/main.py` — FastAPI: auth (register/OTP-verify/login JWT), responder
  location/availability, `/sos` (PostGIS 3km query + FCM push), `/fcm/token`,
  dashboard endpoints, `/responders/nearby`
- `backend/` — requirements.txt includes firebase-admin; Firebase key mounted via
  Render **Secret File** `service-account.json` + env `FIREBASE_KEY_PATH=/etc/secrets/service-account.json`
- `src/` — Next.js dashboard; env `RESQMESH_API_URL` points to Render backend;
  `src/db/index.ts` uses LAZY db init (must not throw at build time)
- `android/` — Kotlin app `com.resqnet.app`, base URL `https://resqmesh-api-3g1c.onrender.com`
  in ResQApi.kt; timeouts 20s/45s (free-tier cold start); Responders tab fetches
  live `/responders/nearby`; FCM high-priority alarm notifications; SMS + BLE mesh in app
- `vercel.json` — "Services" preset, single service `app` (root ".", nextjs)

## Deployments config
- Render: service `resqmesh-api` (Python 3, region Singapore, root `backend`,
  build `pip install -r requirements.txt`, start `uvicorn app.main:app --host 0.0.0.0 --port $PORT`)
  env: DATABASE_URL, JWT_SECRET, FIREBASE_KEY_PATH (+ secret file)
  `.python-version` pins 3.12.3 (Python 3.14 broke pydantic-core builds!)
- Vercel: project resqmesh (`reshqmesh-six.vercel.app`), env `RESQMESH_API_URL`

## Test data (registered on live server)
- demodoctor@resqmesh-demo.org / demonurse@... / demovolunteer@resqmesh-demo.org — password `secret123`
- Locations ~17.37-17.39, 78.48-78.50 (Hyderabad), radius 3km
- Real users from phone: vishal@gmail.com (has GPS), sujatha@gmail.com (location MISSING)

## Key lessons / gotchas
- NEVER import `src/db` at module top-level without lazy init (Vercel build crashes)
- API `POST /auth/register` returns demo OTP directly (no email service yet)
- bcrypt 72-byte limit handled by SHA-256 fallback in ResQApi.kt
- Responders appear ONLY within 3km of phone GPS — empty list elsewhere is CORRECT
- Demo accounts' coords are near Hyderabad; relocate via `/responders/location` if testing elsewhere
- Free tier sleep: open /health before demos; permanent fix = paid plan or keep-alive pinger

## Pending / next steps
1. Rebuild APK with latest commits (c42688c live Responders tab + af62d7d timeouts) and test on phones
2. sujatha's device: fix location sync (GPS permission / cold start)
3. Optional: keep-alive pinger for Render; paid hosting for real use
4. Future features: OTP via email, Google Maps view of alerts, SOS resolution workflow,
   push delivery confirmation, README for GitHub
