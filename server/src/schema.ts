import { pgTable, text, timestamp, boolean, index, integer, date, primaryKey, uniqueIndex, check } from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

export const user = pgTable('user', {
  id: text('id').primaryKey(), name: text('name').notNull(), email: text('email').notNull().unique(),
  emailVerified: boolean('email_verified').default(false).notNull(), image: text('image'),
  createdAt: timestamp('created_at').defaultNow().notNull(), updatedAt: timestamp('updated_at').defaultNow().notNull(),
});
export const session = pgTable('session', {
  id: text('id').primaryKey(), expiresAt: timestamp('expires_at').notNull(), token: text('token').notNull().unique(),
  createdAt: timestamp('created_at').defaultNow().notNull(), updatedAt: timestamp('updated_at').defaultNow().notNull(),
  ipAddress: text('ip_address'), userAgent: text('user_agent'),
  userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
}, (t) => [index('session_user_idx').on(t.userId)]);
export const account = pgTable('account', {
  id: text('id').primaryKey(), accountId: text('account_id').notNull(), providerId: text('provider_id').notNull(),
  userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
  accessToken: text('access_token'), refreshToken: text('refresh_token'), idToken: text('id_token'),
  accessTokenExpiresAt: timestamp('access_token_expires_at'), refreshTokenExpiresAt: timestamp('refresh_token_expires_at'),
  scope: text('scope'), password: text('password'), createdAt: timestamp('created_at').defaultNow().notNull(),
  updatedAt: timestamp('updated_at').defaultNow().notNull(),
}, (t) => [index('account_user_idx').on(t.userId)]);
export const verification = pgTable('verification', {
  id: text('id').primaryKey(), identifier: text('identifier').notNull(), value: text('value').notNull(),
  expiresAt: timestamp('expires_at').notNull(), createdAt: timestamp('created_at').defaultNow().notNull(),
  updatedAt: timestamp('updated_at').defaultNow().notNull(),
}, (t) => [index('verification_identifier_idx').on(t.identifier)]);


export const friendship = pgTable('friendship', {
  id: text('id').primaryKey(),
  requester: text('requester').notNull().references(() => user.id, { onDelete: 'cascade' }),
  recipient: text('recipient').notNull().references(() => user.id, { onDelete: 'cascade' }),
  status: text('status').notNull().default('pending'),
}, t => [
  uniqueIndex('friendship_pair').on(sql`least(${t.requester}, ${t.recipient})`, sql`greatest(${t.requester}, ${t.recipient})`),
  check('friendship_not_self', sql`${t.requester} <> ${t.recipient}`),
  check('friendship_status', sql`${t.status} in ('pending', 'accepted')`),
]);
export const stepGroup = pgTable('step_group', {
  id: text('id').primaryKey(), name: text('name').notNull(), code: text('code').notNull().unique(),
});
export const groupMember = pgTable('group_member', {
  groupId: text('group_id').notNull().references(() => stepGroup.id, { onDelete: 'cascade' }),
  userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
}, t => [primaryKey({ columns: [t.groupId, t.userId] })]);
export const stepSharing = pgTable('step_sharing', {
  userId: text('user_id').primaryKey().references(() => user.id, { onDelete: 'cascade' }),
  enabled: boolean('enabled').notNull().default(false),
});
export const sharedSteps = pgTable('shared_steps', {
  userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
  date: date('date').notNull(), steps: integer('steps').notNull(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
}, t => [primaryKey({ columns: [t.userId, t.date] }), check('non_negative_steps', sql`${t.steps} >= 0`)]);
