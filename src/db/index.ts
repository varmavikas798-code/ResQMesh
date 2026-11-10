import { drizzle } from "drizzle-orm/node-postgres";
import { Pool } from "pg";

const globalForDb = globalThis as typeof globalThis & {
  __resqnetPostgresqlPool?: Pool;
  __resqnetDrizzleDb?: ReturnType<typeof drizzle>;
};

function createDb() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error("DATABASE_URL is required");
  }
  if (!globalForDb.__resqnetPostgresqlPool) {
    const pool = new Pool({ connectionString: databaseUrl });
    if (process.env.NODE_ENV !== "production") {
      globalForDb.__resqnetPostgresqlPool = pool;
    }
    globalForDb.__resqnetDrizzleDb = drizzle(pool);
  }
  return globalForDb.__resqnetDrizzleDb!;
}

export const db = new Proxy({} as ReturnType<typeof drizzle>, {
  get(_target, property, receiver) {
    return Reflect.get(createDb(), property, receiver);
  },
});
