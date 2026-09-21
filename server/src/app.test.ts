import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from './app.js';
import { loadEnv } from './env.js';
import { createAuth } from './auth.js';
import pg from 'pg';

const env = { BETTER_AUTH_URL: 'http://localhost:3000', BETTER_AUTH_SECRET: 'test-only-random-looking-secret-1234567890', DATABASE_URL: 'postgresql://localhost/unused', GOOGLE_CLIENT_ID: 'web.apps.googleusercontent.com', GOOGLE_ANDROID_CLIENT_ID: 'android.apps.googleusercontent.com', GOOGLE_CLIENT_SECRET: 'test-only' };
const signedIn = {
  user: { id: 'u1', name: 'Test Walker', email: 'walker@example.com', image: null, emailVerified: true, createdAt: new Date(), updatedAt: new Date() },
  session: { id: 's1', token: 'private-session', userId: 'u1', expiresAt: new Date(), createdAt: new Date(), updatedAt: new Date(), ipAddress: null, userAgent: null },
};
const app = createApp({ handler: async () => new Response('auth-handler', { headers: { 'set-auth-token': 'test-session' } }),
  api: { getSession: (async ({ headers }: { headers: Headers }) => headers.get('Authorization') === 'Bearer valid' ? signedIn : null) as ReturnType<typeof createAuth>['api']['getSession'] } });

test('health is public; profile and future social routes require a session', async () => {
  assert.equal((await app.request('/health')).status, 200);
  for (const route of ['/api/me', '/api/friends', '/api/leaderboard']) {
    assert.equal((await app.request(route)).status, 401);
    assert.equal((await app.request(route, { headers: { Authorization: 'Bearer invalid' } })).status, 401);
  }
});
test('me exposes profile but never session token or steps; stubs return 501', async () => {
  const headers = { Authorization: 'Bearer valid' };
  const response = await app.request('/api/me', { headers });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { user: { id: 'u1', name: 'Test Walker', email: 'walker@example.com', image: null }, syncEnabled: false });
  for (const route of ['/api/friends', '/api/leaderboard']) assert.equal((await app.request(route, { headers })).status, 501);
});
test('auth responses preserve bearer header', async () => {
  const response = await app.request('/api/auth/sign-in/social', { method: 'POST' });
  assert.equal(response.headers.get('set-auth-token'), 'test-session');
});
test('environment rejects missing values, placeholders, insecure production URL and invalid port', () => {
  assert.throws(() => loadEnv({}), /Missing/);
  assert.throws(() => loadEnv({ ...env, BETTER_AUTH_SECRET: 'replace-with-a-random-secret-at-least-32-characters' }), /random secret/);
  assert.throws(() => loadEnv({ ...env, NODE_ENV: 'production' }), /HTTPS/);
  assert.throws(() => loadEnv({ ...env, PORT: 'NaN' }), /PORT/);
  assert.equal(loadEnv(env).googleAndroidClientId, env.GOOGLE_ANDROID_CLIENT_ID);
});
test('real Better Auth rejects malformed Google token before database access', async () => {
  const pool = new pg.Pool({ connectionString: env.DATABASE_URL });
  try {
    const auth = createAuth(loadEnv(env), pool);
    assert.deepEqual(auth.options.socialProviders?.google?.clientId, [env.GOOGLE_CLIENT_ID, env.GOOGLE_ANDROID_CLIENT_ID]);
    const response = await createApp(auth).request('http://localhost:3000/api/auth/sign-in/social', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider: 'google', idToken: { token: 'invalid-token' } }),
    });
    assert.equal(response.status, 401);
    assert.equal(response.headers.get('set-auth-token'), null);
  } finally { await pool.end(); }
});
