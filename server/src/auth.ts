import { betterAuth } from 'better-auth';
import { bearer } from 'better-auth/plugins';
import { drizzleAdapter } from '@better-auth/drizzle-adapter';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { Pool } from 'pg';
import type { loadEnv } from './env.js';
import * as schema from './schema.js';

export function createAuth(env: ReturnType<typeof loadEnv>, pool: Pool) {
  return betterAuth({
    appName: 'Step Counter', baseURL: env.url, secret: env.secret,
    database: drizzleAdapter(drizzle(pool, { schema }), { provider: 'pg', schema }),
    socialProviders: { google: {
      clientId: [env.googleClientId, env.googleAndroidClientId],
      clientSecret: env.googleClientSecret,
    } },
    plugins: [bearer()],
  });
}
