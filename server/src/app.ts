import { Hono } from 'hono';
import type { Pool } from 'pg';
import { socialRoutes, type SocialEnv } from './social.js';
import type { createAuth } from './auth.js';

type Auth = ReturnType<typeof createAuth>;
export function createApp(auth: Pick<Auth, 'handler'> & { api: Pick<Auth['api'], 'getSession'> }, pool?: Pool) {
  const app = new Hono<SocialEnv>();
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
  app.use('/api/*', async (c, next) => {
    const session = await auth.api.getSession({ headers: c.req.raw.headers });
    if (!session) return c.json({ error: 'Unauthorized' }, 401);
    if (!pool) return c.json({ error: 'Social database unavailable' }, 503);
    // The social subrouter consumes this authenticated identity only.
    c.set('userId', session.user.id);
    await next();
  });
  if (pool) app.route('/api', socialRoutes(pool));
  return app;
}
