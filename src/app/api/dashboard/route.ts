export const dynamic = "force-dynamic";

const apiBase = (process.env.RESQMESH_API_URL || "http://127.0.0.1:8000").replace(/\/$/, "");

export async function GET() {
  try {
    const [alertsResponse, respondersResponse] = await Promise.all([
      fetch(`${apiBase}/dashboard/alerts?limit=100`, { cache: "no-store" }),
      fetch(`${apiBase}/dashboard/responders`, { cache: "no-store" }),
    ]);

    if (!alertsResponse.ok || !respondersResponse.ok) {
      return Response.json(
        { error: `ResQMesh API returned HTTP ${!alertsResponse.ok ? alertsResponse.status : respondersResponse.status}` },
        { status: 502 },
      );
    }

    const [alerts, responders] = await Promise.all([alertsResponse.json(), respondersResponse.json()]);
    return Response.json({ alerts, responders });
  } catch {
    return Response.json({ error: "Could not reach the ResQMesh API." }, { status: 502 });
  }
}
