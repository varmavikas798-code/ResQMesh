"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

type Row = Record<string, unknown>;
type DashboardData = { alerts: Row[]; responders: Row[]; updatedAt: string; error?: string };

function rows(value: unknown): Row[] {
  if (Array.isArray(value)) return value.filter((item): item is Row => !!item && typeof item === "object");
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    for (const key of ["items", "results", "alerts", "responders", "data"]) {
      if (Array.isArray(record[key])) return rows(record[key]);
    }
  }
  return [];
}

function text(value: unknown, fallback = "—") {
  if (value === null || value === undefined || value === "") return fallback;
  if (typeof value === "boolean") return value ? "Available" : "Unavailable";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function first(row: Row, keys: string[], fallback = "—") {
  for (const key of keys) if (row[key] !== undefined && row[key] !== null && row[key] !== "") return text(row[key], fallback);
  return fallback;
}

function location(row: Row) {
  const lat = row.latitude ?? row.lat;
  const lon = row.longitude ?? row.lon ?? row.lng;
  return lat !== undefined && lon !== undefined ? `${text(lat)}, ${text(lon)}` : first(row, ["location", "address"]);
}

function date(value: unknown) {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? text(value) : parsed.toLocaleString();
}

function statusTone(value: string) {
  const normalized = value.toLowerCase();
  if (["active", "available", "resolved", "accepted", "completed"].some((word) => normalized.includes(word))) return "good";
  if (["pending", "open", "sent", "new"].some((word) => normalized.includes(word))) return "pending";
  return "neutral";
}

function Badge({ value }: { value: string }) {
  return <span className={`badge badge-${statusTone(value)}`}><i />{value}</span>;
}

export default function HomePage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeSection, setActiveSection] = useState("overview");

  const navigateTo = (section: string) => {
    setActiveSection(section);
  };

  const refresh = useCallback(async (quiet = false) => {
    if (quiet) setRefreshing(true);
    try {
      const response = await fetch("/api/dashboard", { cache: "no-store" });
      const result = await response.json();
      setData({
        alerts: rows(result.alerts),
        responders: rows(result.responders),
        updatedAt: new Date().toISOString(),
        error: response.ok ? undefined : result.error || "Backend is unavailable",
      });
    } catch {
      setData({ alerts: [], responders: [], updatedAt: new Date().toISOString(), error: "Dashboard could not reach the backend." });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(true), 15_000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const pendingAlerts = useMemo(() => data?.alerts.filter((item) => {
    const status = first(item, ["status", "state"], "pending").toLowerCase();
    return !["resolved", "completed", "closed"].includes(status);
  }).length ?? 0, [data]);

  return (
    <main className="dashboard-shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="ResQNet home">
          <span className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 48 48" role="img">
              <path fill="none" stroke="#fff" d="M24 6 9 12v12c0 10 7 17 15 20 8-3 15-10 15-20V12L24 6Z" />
              <path fill="none" stroke="#fff" d="M14 25h6l3-7 3 14 3-7h5" />
            </svg>
          </span>
          <span className="wordmark"><span>ResQ</span><span>Net</span></span>
        </a>
        <div className="workspace-label">EMERGENCY RESPONSE</div>
        <nav className="nav-list" aria-label="Dashboard navigation">
          <button className={`nav-item ${activeSection === "overview" ? "active" : ""}`} aria-current={activeSection === "overview" ? "page" : undefined} onClick={() => navigateTo("overview")}><span className="nav-icon">⌂</span>Overview</button>
          <button className={`nav-item ${activeSection === "alerts" ? "active" : ""}`} aria-current={activeSection === "alerts" ? "page" : undefined} onClick={() => navigateTo("alerts")}><span className="nav-icon">!</span>SOS alerts<span className="nav-count">{data?.alerts.length ?? "—"}</span></button>
          <button className={`nav-item ${activeSection === "responders" ? "active" : ""}`} aria-current={activeSection === "responders" ? "page" : undefined} onClick={() => navigateTo("responders")}><span className="nav-icon">◎</span>Responders<span className="nav-count">{data?.responders.length ?? "—"}</span></button>
        </nav>
        <div className="sidebar-bottom">
          <div className="server-card">
            <span className={`server-dot ${data && !data.error ? "online" : "offline"}`} />
            <div><strong>API server</strong><small>{data && !data.error ? "Connected" : "Not connected"}</small></div>
          </div>
          <p>ResQNet operations dashboard</p>
        </div>
      </aside>

      <section className="main-panel" id="overview">
        <header className="topbar">
          <div><p className="eyebrow">OPERATIONS</p><h1>{activeSection === "alerts" ? "SOS alerts" : activeSection === "responders" ? "Responders" : "Overview"}</h1></div>
          <div className="topbar-actions">
            <span className="updated-label">{data ? `Updated ${new Date(data.updatedAt).toLocaleTimeString()}` : "Connecting…"}</span>
            <button className="refresh-button" onClick={() => void refresh(true)} disabled={refreshing}>
              <span className={refreshing ? "spin" : ""}>↻</span> Refresh
            </button>
          </div>
        </header>

        {data?.error && <div className="connection-warning"><strong>Backend connection failed</strong><span>{data.error}. Check that FastAPI is running and RESQMESH_API_URL points to it.</span></div>}

        <div className="welcome-row">
          <div><h2>{activeSection === "alerts" ? "Emergency requests" : activeSection === "responders" ? "Responder directory" : "Emergency response at a glance"}</h2><p>{activeSection === "alerts" ? "SOS alerts received and stored by the backend." : activeSection === "responders" ? "Responder profiles saved in the backend." : "Live SOS and responder records from the ResQNet backend."}</p></div>
          <div className={`connection-pill ${data && !data.error ? "connected" : "disconnected"}`}><i />{data && !data.error ? "Backend connected" : "Connecting to backend"}</div>
        </div>

        {activeSection === "overview" && <section className="metric-grid" aria-label="Summary">
          <article className="metric-card"><div className="metric-heading"><span className="metric-icon red">!</span><span className="metric-caption">ALL SOS ALERTS</span></div><strong>{loading ? "…" : data?.alerts.length ?? 0}</strong><small>Recorded by the backend</small></article>
          <article className="metric-card"><div className="metric-heading"><span className="metric-icon amber">◷</span><span className="metric-caption">OPEN ALERTS</span></div><strong>{loading ? "…" : pendingAlerts}</strong><small>Not marked resolved</small></article>
          <article className="metric-card"><div className="metric-heading"><span className="metric-icon green">◎</span><span className="metric-caption">RESPONDERS</span></div><strong>{loading ? "…" : data?.responders.length ?? 0}</strong><small>Registered in the backend</small></article>
        </section>}

        {(activeSection === "overview" || activeSection === "alerts") && <section className="data-card" id="alerts">
          <div className="section-heading"><div><h2>Recent SOS alerts</h2><p>Latest emergency requests received by the API</p></div><span className="table-total">{data?.alerts.length ?? 0} total</span></div>
          <div className="table-wrap"><table><thead><tr><th>REQUEST</th><th>LOCATION</th><th>STATUS</th><th>RECEIVED</th></tr></thead>
            <tbody>{loading ? <tr><td colSpan={4} className="empty-cell">Loading alerts…</td></tr> : data?.alerts.length ? data.alerts.map((alert, index) => {
              const status = first(alert, ["status", "state"], "Received");
              return <tr key={first(alert, ["id", "alert_id"], String(index))}><td><strong>{first(alert, ["message", "title", "type"], "SOS request")}</strong><small>#{first(alert, ["id", "alert_id"], "—")}</small></td><td className="mono">{location(alert)}</td><td><Badge value={status} /></td><td>{date(alert.created_at ?? alert.createdAt ?? alert.timestamp)}</td></tr>;
            }) : <tr><td colSpan={4} className="empty-cell"><span className="empty-symbol">✓</span><strong>No SOS alerts yet</strong><small>New requests saved by the Android app will appear here.</small></td></tr>}</tbody>
          </table></div>
        </section>}

        {(activeSection === "overview" || activeSection === "responders") && <section className="data-card" id="responders">
          <div className="section-heading"><div><h2>Responders</h2><p>Responder records available from the backend</p></div><span className="table-total">{data?.responders.length ?? 0} total</span></div>
          <div className="table-wrap"><table><thead><tr><th>RESPONDER</th><th>QUALIFICATION</th><th>LOCATION</th><th>AVAILABILITY</th></tr></thead>
            <tbody>{loading ? <tr><td colSpan={4} className="empty-cell">Loading responders…</td></tr> : data?.responders.length ? data.responders.map((person, index) => {
              const status = first(person, ["availability", "status", "available"], "Registered");
              return <tr key={first(person, ["id", "responder_id"], String(index))}><td><strong>{first(person, ["name", "full_name"], "Responder")}</strong><small>{first(person, ["email", "phone"], "")}</small></td><td>{first(person, ["qualification", "role", "specialization"])}</td><td className="mono">{location(person)}</td><td><Badge value={status} /></td></tr>;
            }) : <tr><td colSpan={4} className="empty-cell"><span className="empty-symbol people">◎</span><strong>No responders registered</strong><small>Responders will appear once their profiles are saved to the backend.</small></td></tr>}</tbody>
          </table></div>
        </section>}
        <footer className="footer-note">ResQNet · Emergency response dashboard <span>Refreshes automatically every 15 seconds</span></footer>
      </section>
    </main>
  );
}
