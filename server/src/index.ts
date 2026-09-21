import { serve } from '@hono/node-server';
import pg from 'pg';
import { loadEnv } from './env.js';
import { createAuth } from './auth.js';
import { createApp } from './app.js';

const env = loadEnv();
const pool = new pg.Pool({ connectionString: env.databaseUrl });
const app = createApp(createAuth(env, pool));
const server = serve({ fetch: app.fetch, port: env.port, hostname: '0.0.0.0' });
for (const signal of ['SIGTERM', 'SIGINT'] as const) {
  process.on(signal, () => {
    server.close(() => { void pool.end().then(() => process.exit(0)); });
    setTimeout(() => process.exit(1), 10_000).unref();
  });
}
