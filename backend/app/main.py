import json
import os
import random
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Literal
from uuid import uuid4

from fastapi import Depends, FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from jose import jwt, JWTError
from passlib.context import CryptContext
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
import firebase_admin
from firebase_admin import credentials, messaging

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://resqmesh:resqmesh_demo@localhost:5432/resqmesh")
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)
    if "?" not in DATABASE_URL:
        DATABASE_URL += "?sslmode=require"
SECRET = os.getenv("JWT_SECRET", "replace-this-demo-secret-before-deployment")
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
Session = sessionmaker(engine)
pwd = CryptContext(schemes=["bcrypt"], deprecated="auto")

import base64

# Firebase Initialization
try:
    _b64 = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    if _b64:
        cred = credentials.Certificate(json.loads(base64.b64decode(_b64)))
        firebase_admin.initialize_app(cred)
    elif os.path.exists(os.getenv("FIREBASE_KEY_PATH", "")):
        cred = credentials.Certificate(os.getenv("FIREBASE_KEY_PATH"))
        firebase_admin.initialize_app(cred)
    else:
        print("Firebase not configured (no key found) - push disabled")
except Exception as e:
    print(f"Firebase init failed: {e}")

app = FastAPI(title="ResQMesh API", version="1.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

class Register(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    email: EmailStr
    phone: str = Field(pattern=r"^\d{10}$")
    password: str = Field(min_length=6)
    qualification: Literal["Doctor", "Nurse", "Paramedic", "CPR Volunteer"]

class Verify(BaseModel):
    email: EmailStr
    otp: str = Field(pattern=r"^\d{6}$")

class Login(BaseModel):
    email: EmailStr
    password: str

class Location(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)

class Availability(BaseModel):
    available: bool

class SOS(Location):
    message: str = Field(default="Emergency assistance needed", max_length=500)

@contextmanager
def db():
    with engine.begin() as c:
        c.execute(text("CREATE EXTENSION IF NOT EXISTS postgis"))
        c.execute(text("""CREATE TABLE IF NOT EXISTS users (
          id UUID PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL,
          phone TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, qualification TEXT NOT NULL,
          verified BOOLEAN NOT NULL DEFAULT FALSE, available BOOLEAN NOT NULL DEFAULT TRUE,
          fcm_token TEXT, location GEOGRAPHY(POINT,4326), created_at TIMESTAMPTZ NOT NULL DEFAULT now())"""))
        c.execute(text("""CREATE TABLE IF NOT EXISTS otps (
          email TEXT PRIMARY KEY, code TEXT NOT NULL, payload JSONB NOT NULL,
          expires_at TIMESTAMPTZ NOT NULL)"""))
        c.execute(text("""CREATE TABLE IF NOT EXISTS alerts (
          id UUID PRIMARY KEY, user_id UUID REFERENCES users(id), message TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'active', location GEOGRAPHY(POINT,4326) NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now())"""))
        c.execute(text("CREATE INDEX IF NOT EXISTS users_location_gix ON users USING GIST(location)"))
        c.execute(text("CREATE INDEX IF NOT EXISTS alerts_location_gix ON alerts USING GIST(location)"))
    session = Session()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()

@app.on_event("startup")
def startup():
    with db():
        pass

def current_user(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Sign in required")
    try:
        uid = jwt.decode(authorization[7:], SECRET, algorithms=["HS256"])["sub"]
    except (JWTError, KeyError):
        raise HTTPException(401, "Invalid session")
    with db() as s:
        user = s.execute(text("SELECT id,name,email,phone,qualification,available, ST_Y(location::geometry) lat, ST_X(location::geometry) lon FROM users WHERE id=:id AND verified"), {"id": uid}).mappings().first()
    if not user: raise HTTPException(401, "Account not found")
    return dict(user)

def token(uid):
    return jwt.encode({"sub": str(uid), "exp": datetime.now(timezone.utc)+timedelta(days=7)}, SECRET, algorithm="HS256")

@app.get("/health")
def health():
    with db() as s: s.execute(text("SELECT PostGIS_Full_Version()"))
    return {"ok": True, "database": "PostgreSQL + PostGIS"}

@app.post("/auth/register")
def register(body: Register):
    email = body.email.lower()
    with db() as s:
        if s.execute(text("SELECT 1 FROM users WHERE email=:e OR phone=:p"), {"e": email, "p": body.phone}).first(): raise HTTPException(409, "Email or phone already registered")
        code = os.getenv("DEMO_OTP") or f"{random.randint(0,999999):06d}"
        payload = body.model_dump(exclude={"email"}); payload["password"] = pwd.hash(body.password)
        s.execute(text("INSERT INTO otps(email,code,payload,expires_at) VALUES(:e,:c,CAST(:p AS JSONB),now()+interval '10 minutes') ON CONFLICT(email) DO UPDATE SET code=:c,payload=CAST(:p AS JSONB),expires_at=now()+interval '10 minutes'"), {"e": email,"c": code,"p": json.dumps(payload)})
    return {"ok": True, "message": "Demo OTP issued", "demo_otp": code}

@app.post("/auth/verify")
def verify(body: Verify):
    with db() as s:
        row=s.execute(text("SELECT code,payload FROM otps WHERE email=:e AND expires_at>now()"), {"e":body.email.lower()}).mappings().first()
        if not row or row["code"] != body.otp: raise HTTPException(400,"OTP invalid or expired")
        p=row["payload"]; uid=uuid4()
        s.execute(text("INSERT INTO users(id,name,email,phone,password_hash,qualification,verified) VALUES(:id,:n,:e,:p,:h,:q,true)"), {"id":uid,"n":p["name"],"e":body.email.lower(),"p":p["phone"],"h":p["password"],"q":p["qualification"]})
        s.execute(text("DELETE FROM otps WHERE email=:e"), {"e":body.email.lower()})
    return {"token":token(uid),"user":{"id":str(uid),"name":p["name"],"email":body.email.lower(),"phone":p["phone"],"qualification":p["qualification"]}}

@app.post("/auth/login")
def login(body: Login):
    with db() as s: row=s.execute(text("SELECT id,password_hash,name,email,phone,qualification FROM users WHERE email=:e AND verified"), {"e":body.email.lower()}).mappings().first()
    if not row or not pwd.verify(body.password,row["password_hash"]): raise HTTPException(401,"Incorrect email or password")
    return {"token":token(row["id"]),"user":{k:row[k] for k in ("id","name","email","phone","qualification")}}

@app.get("/me")
def me(user=Depends(current_user)): return user

@app.put("/responders/location")
def set_location(body: Location, user=Depends(current_user)):
    with db() as s: s.execute(text("UPDATE users SET location=ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography WHERE id=:id"), {"lon":body.longitude,"lat":body.latitude,"id":user["id"]})
    return {"ok":True}

@app.put("/responders/availability")
def set_availability(body: Availability, user=Depends(current_user)):
    with db() as s:
        s.execute(text("UPDATE users SET available=:available WHERE id=:id"), {"available":body.available,"id":user["id"]})
    return {"ok":True,"available":body.available}

@app.put("/fcm/token")
def update_fcm_token(token: str = Header(None), user=Depends(current_user)):
    if not token: raise HTTPException(400, "Token missing")
    with db() as s:
        s.execute(text("UPDATE users SET fcm_token=:t WHERE id=:id"), {"t": token, "id": user["id"]})
    return {"ok": True}

@app.get("/responders/nearby")
def nearby(lat: float, lon: float, radius_m: int=3000, user=Depends(current_user)):
    with db() as s:
        rows=s.execute(text("""SELECT id,name,qualification,ST_Y(location::geometry) latitude,ST_X(location::geometry) longitude,
          round((ST_Distance(location,ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography)/1000)::numeric,2) distance_km
          FROM users WHERE verified AND available AND location IS NOT NULL AND id<>:uid
          AND ST_DWithin(location,ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography,:radius) ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography"""), {"lon":lon,"lat":lat,"radius":min(radius_m,10000),"uid":user["id"]}).mappings().all()
    return [dict(x) for x in rows]

def send_push_notification(token: str, title: str, body: str):
    try:
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            token=token,
            android=messaging.AndroidConfig(priority='high')
        )
        messaging.send(message)
    except Exception as e:
        print(f"Failed to send push to {token}: {e}")

@app.post("/sos")
def create_sos(body: SOS, user=Depends(current_user)):
    aid=uuid4()
    with db() as s:
        s.execute(text("INSERT INTO alerts(id,user_id,message,location) VALUES(:id,:uid,:m,ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography)"), {"id":aid,"uid":user["id"],"m":body.message,"lon":body.longitude,"lat":body.latitude})
        responders=s.execute(text("""SELECT id,name,qualification,phone,fcm_token,ST_Y(location::geometry) latitude,ST_X(location::geometry) longitude,
          round((ST_Distance(location,ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography)/1000)::numeric,2) distance_km
          FROM users WHERE verified AND available AND location IS NOT NULL AND id<>:uid
          AND ST_DWithin(location,ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography,3000) ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography"""), {"uid":user["id"],"lon":body.longitude,"lat":body.latitude}).mappings().all()
    
    # Trigger Instant Push Notifications to all nearby responders
    for r in responders:
        if r.get("fcm_token"):
            send_push_notification(
                r["fcm_token"], 
                "🚨 EMERGENCY SOS!", 
                f"Urgent help needed {r['distance_km']}km away! {body.message}"
            )
            
    return {"id":str(aid),"status":"active","created_at":datetime.now(timezone.utc).isoformat(),"responders_notified":len(responders),"responders":[dict(x) for x in responders],"delivery":"Push notifications sent via Firebase"}

@app.get("/dashboard/alerts")
def dashboard_alerts(limit: int=100):
    with db() as s: rows=s.execute(text("""SELECT a.id,a.message,a.status,a.created_at,u.name reporter,
      ST_Y(a.location::geometry) latitude,ST_X(a.location::geometry) longitude,
      (SELECT count(*) FROM users r WHERE r.verified AND r.available AND r.location IS NOT NULL AND r.id<>a.user_id AND ST_DWithin(r.location,a.location,3000)) responder_count
      FROM alerts a JOIN users u ON u.id=a.user_id ORDER BY a.created_at DESC LIMIT :n"""), {"n":min(limit,500)}).mappings().all()
    return [dict(x) for x in rows]

@app.get("/dashboard/responders")
def dashboard_responders():
    # Show every verified responder profile in the operations dashboard. Location
    # is optional: a newly registered responder should still appear before GPS is
    # granted, while nearby matching continues to require a real saved location.
    with db() as s: rows=s.execute(text("""SELECT id,name,email,phone,qualification,available,
      ST_Y(location::geometry) latitude,ST_X(location::geometry) longitude
      FROM users WHERE verified ORDER BY created_at DESC""")).mappings().all()
    return [dict(x) for x in rows]
