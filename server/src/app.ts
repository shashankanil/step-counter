import { Hono } from 'hono';
import type { createAuth } from './auth.js';

type Auth = ReturnType<typeof createAuth>;
export function createApp(auth: Pick<Auth, 'handler'> & { api: Pick<Auth['api'], 'getSession'> }) {
  const app = new Hono();
  app.onError((_error, c) => c.json({ error: 'Internal server error' }, 500));
  app.get('/health', (c) => c.json({ status: 'ok', service: 'step-counter', phase: 3 }));
  app.use('/api/*', async (c, next) => {
    c.header('Cache-Control', 'no-store');
    await next();
  });
  app.on(['GET', 'POST'], '/api/auth/*', (c) => auth.handler(c.req.raw));
  app.get('/api/me', async (c) => {
    const session = await auth.api.getSession({ headers: c.req.raw.headers });
    if (!session) return c.json({ error: 'Unauthorized' }, 401);
    const { id, name, email, image } = session.user;
    return c.json({ user: { id, name, email, image }, syncEnabled: false });
  });
  for (const path of ['/api/friends', '/api/leaderboard']) {
    app.get(path, async (c) => {
      const session = await auth.api.getSession({ headers: c.req.raw.headers });
      if (!session) return c.json({ error: 'Unauthorized' }, 401);
      return c.json({ error: 'Not implemented', phase: 4 }, 501);
    });
  }
  return app;
}
