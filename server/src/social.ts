import { Hono } from 'hono';
import type { Pool } from 'pg';
import { randomUUID, randomBytes } from 'node:crypto';

export type SocialEnv = { Variables: { userId: string } };
export function socialRoutes(pool: Pool) {
  const app = new Hono<SocialEnv>();
  app.get('/friends', async c => {
    const result = await pool.query(`SELECT f.id, f.status, f.requester = $1 AS outgoing,
      u.id AS "userId", u.name, u.email FROM friendship f JOIN "user" u
      ON u.id = CASE WHEN f.requester = $1 THEN f.recipient ELSE f.requester END
      WHERE f.requester = $1 OR f.recipient = $1 ORDER BY u.name`, [c.get('userId')]);
    return c.json({ friends: result.rows });
  });
  app.post('/friends', async c => {
    const body = await c.req.json().catch(() => null);
    const handle = typeof body?.handle === 'string' ? body.handle.trim().toLowerCase() : '';
    if (!handle || handle.length > 254) return c.json({ error: 'Enter an account email address.' }, 400);
    const user = await pool.query('SELECT id FROM "user" WHERE lower(email) = $1', [handle]);
    if (!user.rowCount) return c.json({ error: 'No account found. Ask your friend to connect their Google account first.' }, 404);
    const id = c.get('userId'), other = user.rows[0].id;
    if (id === other) return c.json({ error: 'Choose another account.' }, 400);
    const result = await pool.query(`INSERT INTO friendship(id, requester, recipient, status)
      VALUES ($1, $2, $3, 'pending') ON CONFLICT DO NOTHING RETURNING id`, [randomUUID(), id, other]);
    return c.json({ message: result.rowCount ? 'Request sent' : 'A relationship already exists' });
  });
  app.post('/friends/:id/accept', async c => {
    const result = await pool.query(`UPDATE friendship SET status = 'accepted'
      WHERE id = $1 AND recipient = $2 AND status = 'pending' RETURNING id`, [c.req.param('id'), c.get('userId')]);
    return result.rowCount ? c.json({ accepted: true }) : c.json({ error: 'Request not found.' }, 404);
  });
  app.get('/groups', async c => {
    const result = await pool.query(`SELECT g.id, g.name, g.code FROM step_group g
      JOIN group_member m ON m.group_id = g.id WHERE m.user_id = $1 ORDER BY g.name`, [c.get('userId')]);
    return c.json({ groups: result.rows });
  });
  app.post('/groups', async c => {
    const body = await c.req.json().catch(() => null);
    const name = typeof body?.name === 'string' ? body.name.trim() : '';
    if (!name || name.length > 60) return c.json({ error: 'Use a group name of 1–60 characters.' }, 400);
    const id = randomUUID(), code = randomBytes(6).toString('hex').toUpperCase();
    // One statement keeps group creation and creator membership atomic.
    await pool.query(`WITH created AS (
      INSERT INTO step_group(id, name, code) VALUES ($1, $2, $3) RETURNING id)
      INSERT INTO group_member(group_id, user_id) SELECT id, $4 FROM created`, [id, name, code, c.get('userId')]);
    return c.json({ id, name, code }, 201);
  });
  app.post('/groups/join', async c => {
    const body = await c.req.json().catch(() => null);
    const code = typeof body?.code === 'string' ? body.code.trim().toUpperCase() : '';
    if (!/^[A-F0-9]{12}$/.test(code)) return c.json({ error: 'Enter the 12-character group invite code.' }, 400);
    const group = await pool.query('SELECT id FROM step_group WHERE code = $1', [code]);
    if (!group.rowCount) return c.json({ error: 'Group code not found.' }, 404);
    await pool.query('INSERT INTO group_member(group_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING', [group.rows[0].id, c.get('userId')]);
    return c.json({ joined: true });
  });
  app.get('/sync', async c => {
    const r = await pool.query('SELECT enabled FROM step_sharing WHERE user_id = $1', [c.get('userId')]);
    return c.json({ enabled: r.rows[0]?.enabled === true });
  });
  app.put('/sync', async c => {
    const body = await c.req.json().catch(() => null);
    if (typeof body?.enabled !== 'boolean') return c.json({ error: 'enabled must be a boolean.' }, 400);
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(`INSERT INTO step_sharing(user_id, enabled) VALUES ($1, $2)
        ON CONFLICT(user_id) DO UPDATE SET enabled = EXCLUDED.enabled`, [c.get('userId'), body.enabled]);
      if (!body.enabled) await client.query('DELETE FROM shared_steps WHERE user_id = $1', [c.get('userId')]);
      await client.query('COMMIT');
    } catch (e) { await client.query('ROLLBACK'); throw e; }
    finally { client.release(); }
    return c.json({ enabled: body.enabled });
  });
  app.put('/steps', async c => {
    const body = await c.req.json().catch(() => null);
    const days: unknown = body?.days;
    if (!Array.isArray(days) || days.length > 31 || days.some(d =>
      !d || typeof d.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d.date) ||
      !Number.isInteger(d.steps) || d.steps < 0 || d.steps > 1_000_000 ||
      !Number.isFinite(Date.parse(d.date)) || new Date(d.date).toISOString().slice(0, 10) !== d.date ||
      Date.parse(d.date) > Date.now() + 86_400_000 || Date.parse(d.date) < Date.now() - 33 * 86_400_000
    ) || new Set(days.map(d => d.date)).size !== days.length) {
      return c.json({ error: 'Supply up to 31 unique recent daily totals.' }, 400);
    }
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const consent = await client.query('SELECT enabled FROM step_sharing WHERE user_id = $1 FOR UPDATE', [c.get('userId')]);
      if (!consent.rows[0]?.enabled) {
        await client.query('ROLLBACK'); return c.json({ error: 'Enable step sharing first.' }, 403);
      }
      for (const day of days) await client.query(`INSERT INTO shared_steps(user_id, date, steps, updated_at) VALUES ($1, $2, $3, now())
        ON CONFLICT(user_id, date) DO UPDATE SET steps = EXCLUDED.steps, updated_at = now()`, [c.get('userId'), day.date, day.steps]);
      await client.query('COMMIT');
    } catch (e) { await client.query('ROLLBACK'); throw e; }
    finally { client.release(); }
    return c.json({ uploaded: days.length });
  });
  app.get('/compare', async c => {
    const me = c.get('userId'), friend = c.req.query('friend'), group = c.req.query('group');
    const date = c.req.query('date') ?? new Date().toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(Date.parse(date)) ||
        new Date(date).toISOString().slice(0, 10) !== date || (!!friend === !!group))
      return c.json({ error: 'Choose one friend or group and a valid date.' }, 400);
    let ids: string[] = [me];
    if (friend) {
      const r = await pool.query(`SELECT id FROM friendship WHERE status = 'accepted'
        AND ((requester = $1 AND recipient = $2) OR (recipient = $1 AND requester = $2))`, [me, friend]);
      if (!r.rowCount) return c.json({ error: 'Accept the friend request before comparing.' }, 403);
      ids.push(friend);
    } else {
      const r = await pool.query('SELECT user_id FROM group_member WHERE group_id = $1 AND EXISTS (SELECT 1 FROM group_member WHERE group_id = $1 AND user_id = $2)', [group, me]);
      if (!r.rowCount) return c.json({ error: 'Join this group before comparing.' }, 403);
      ids = r.rows.map(r => r.user_id);
    }
    const r = await pool.query(`SELECT u.id, u.name,
      COALESCE(p.enabled, false) AS sharing,
      CASE WHEN p.enabled THEN MAX(s.steps) FILTER (WHERE s.date = $2::date) END AS today,
      CASE WHEN p.enabled AND COUNT(s.date) FILTER (WHERE s.date BETWEEN $2::date - 6 AND $2::date) = 7
        THEN SUM(s.steps) FILTER (WHERE s.date BETWEEN $2::date - 6 AND $2::date)::bigint END AS "sevenDay",
      MAX(s.updated_at) AS "updatedAt"
      FROM "user" u LEFT JOIN step_sharing p ON p.user_id = u.id
      LEFT JOIN shared_steps s ON s.user_id = u.id AND p.enabled
      WHERE u.id = ANY($1::text[]) GROUP BY u.id, u.name, p.enabled ORDER BY u.name`, [ids, date]);
    return c.json({ date, selfId: me, members: r.rows.map(r => ({ ...r,
      today: r.today == null ? null : Number(r.today), sevenDay: r.sevenDay == null ? null : Number(r.sevenDay) })) });
  });
  return app;
}
