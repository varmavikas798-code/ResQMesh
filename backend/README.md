# ResQMesh API

FastAPI and PostgreSQL/PostGIS backend shared by the Android app and operations website.

## Run locally

From the project root, run `docker compose up --build -d`. The API is available at
`http://localhost:8000`; interactive API docs are at `http://localhost:8000/docs`.
The Next.js dashboard reads `/dashboard/alerts` and `/dashboard/responders` from
this API. Android emulator requests use `10.0.2.2:8000`, which reaches the same
host port.

The demo OTP is `123456`. Set `DEMO_OTP` in the environment to change it. The
included credentials and JWT secret are for local demonstration only; configure
strong secrets and HTTPS before deploying publicly.

## Project flows

- Responder registration and verification: `POST /auth/register`, then
  `POST /auth/verify`; login: `POST /auth/login`.
- Responder GPS and availability data: `PUT /responders/location`,
  `PUT /responders/availability`, and `GET /responders/nearby`.
- Emergency request: authenticated `POST /sos`; its record is immediately
  visible to the website through `GET /dashboard/alerts`.
- The dashboard lists every verified responder, including profiles that have
  not granted GPS permission yet. Nearby matching only includes responders with
  a saved location. Push, SMS, and Bluetooth mesh delivery need their separate
  provider/device integrations; this API currently records and matches alerts.
