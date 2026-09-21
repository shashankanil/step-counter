import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import type { Pool } from 'pg';
import { createApp } from './app.js';
import type { createAuth } from './auth.js';

test('social flow: requests, recipient-only acceptance, groups, consent and private comparisons', async () => {
  const db = new PGlite();
  try {
    for (const file of ['0000_heavy_ego.sql', '0001_naive_the_watchers.sql']) {
      await db.exec(await readFile(new URL('../drizzle/' + file, import.meta.url), 'utf8'));
    }
    await db.exec(`INSERT INTO "user"(id, name, email) VALUES
      ('a', 'Alice', 'alice@example.com'), ('b', 'Bob', 'bob@example.com'), ('c', 'Cara', 'cara@example.com')`);
    const query = async (sql: string, args?: unknown[]) => {
      const r = await db.query(sql, args);
      return { rows: r.rows, rowCount: r.rows.length || r.affectedRows || 0 };
    };
    const pool = { query, connect: async () => ({ query, release() {} }) } as unknown as Pool;
    const auth = {
      handler: async () => new Response('auth'),
      api: { getSession: (async ({ headers }: { headers: Headers }) => {
        const id = headers.get('authorization')?.replace('Bearer ', '');
        return id && ['a', 'b', 'c'].includes(id) ? { user: { id } } : null;
      }) as ReturnType<typeof createAuth>['api']['getSession'] },
    };
    const app = createApp(auth, pool);
    const req = (id: string, path: string, method = 'GET', body?: unknown) => app.request('/api' + path, {
      method, headers: { Authorization: 'Bearer ' + id, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    assert.equal((await req('', '/friends')).status, 401);
    assert.equal((await req('a', '/friends', 'POST', { handle: 'alice@example.com' })).status, 400);
    assert.equal((await req('a', '/friends', 'POST', { handle: 'nobody@example.com' })).status, 404);
    assert.equal((await req('a', '/friends', 'POST', { handle: 'BOB@example.com' })).status, 200);
    await req('b', '/friends', 'POST', { handle: 'alice@example.com' });
    const friends = await (await req('b', '/friends')).json();
    assert.equal(friends.friends.length, 1);
    assert.equal(friends.friends[0].outgoing, false);
    const requestId = friends.friends[0].id;
    assert.equal((await req('a', '/friends/' + requestId + '/accept', 'POST')).status, 404);
    assert.equal((await req('c', '/friends/' + requestId + '/accept', 'POST')).status, 404);
    assert.equal((await req('a', '/compare?friend=b')).status, 403);
    assert.equal((await req('b', '/friends/' + requestId + '/accept', 'POST')).status, 200);
    assert.equal((await req('a', '/compare?friend=b&group=x')).status, 400);
    assert.equal((await req('a', '/compare?friend=b&date=2026-02-30')).status, 400);
    const today = new Date().toISOString().slice(0, 10);
    const days = Array.from({ length: 7 }, (_, i) => ({
      date: new Date(Date.now() - i * 86_400_000).toISOString().slice(0, 10), steps: 1000 + i,
    }));
    assert.equal((await req('a', '/steps', 'PUT', { days })).status, 403);
    assert.equal((await req('a', '/sync', 'PUT', { enabled: 'yes' })).status, 400);
    assert.equal((await req('a', '/sync', 'PUT', { enabled: true })).status, 200);
    assert.equal((await req('a', '/steps', 'PUT', { days: [{ date: today, steps: -1 }] })).status, 400);
    assert.equal((await req('a', '/steps', 'PUT', { days: [days[0], days[0]] })).status, 400);
    assert.equal((await req('a', '/steps', 'PUT', { days })).status, 200);
    await req('a', '/steps', 'PUT', { days }); // Upserts are idempotent.
    const compared = await (await req('a', '/compare?friend=b&date=' + today)).json();
    assert.equal(compared.members[0].today, 1000);
    assert.equal(compared.members[0].sevenDay, 7021);
    assert.equal(compared.members[1].today, null);
    assert.equal(compared.members[1].sharing, false);
    await req('b', '/sync', 'PUT', { enabled: true });
    await req('b', '/steps', 'PUT', { days: [days[0]] });
    const partial = await (await req('a', '/compare?friend=b')).json();
    assert.equal(partial.members[1].today, 1000);
    assert.equal(partial.members[1].sevenDay, null);
    assert.equal((await req('c', '/compare?friend=a')).status, 403);
    const group = await (await req('a', '/groups', 'POST', { name: 'Morning walkers' })).json();
    assert.match(group.code, /^[A-F0-9]{12}$/);
    assert.equal((await req('b', '/compare?group=' + group.id)).status, 403);
    assert.equal((await req('b', '/groups/join', 'POST', { code: group.code.toLowerCase() })).status, 200);
    await req('b', '/groups/join', 'POST', { code: group.code });
    const groupCompare = await (await req('b', '/compare?group=' + group.id)).json();
    assert.equal(groupCompare.members.length, 2);
    assert.equal((await req('c', '/groups')).status, 200);
    assert.deepEqual((await (await req('c', '/groups')).json()).groups, []);
    assert.equal((await req('a', '/sync', 'PUT', { enabled: false })).status, 200);
    assert.equal((await db.query('SELECT * FROM shared_steps WHERE user_id = \'a\'')).rows.length, 0);
    assert.equal((await req('a', '/steps', 'PUT', { days })).status, 403);
    const disabled = await (await req('b', '/compare?friend=a')).json();
    assert.equal(disabled.members[0].today, null);
  } finally { await db.close(); }
});
