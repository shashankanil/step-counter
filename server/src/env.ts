export function loadEnv(source: NodeJS.ProcessEnv = process.env) {
  const required = (key: string) => {
    const value = source[key]?.trim();
    if (!value) throw new Error(`Missing required environment variable: ${key}`);
    return value;
  };
  const url = required('BETTER_AUTH_URL');
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' && !(source.NODE_ENV !== 'production' && parsed.protocol === 'http:' && ['localhost', '127.0.0.1', '10.0.2.2'].includes(parsed.hostname))) {
    throw new Error('BETTER_AUTH_URL must use HTTPS outside local development');
  }
  const secret = required('BETTER_AUTH_SECRET');
  if (secret.length < 32 || secret.includes('replace-')) throw new Error('BETTER_AUTH_SECRET must be a random secret of at least 32 characters');
  const port = Number(source.PORT ?? 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  return { url, secret, port, databaseUrl: required('DATABASE_URL'), googleClientId: required('GOOGLE_CLIENT_ID'),
    googleClientSecret: required('GOOGLE_CLIENT_SECRET'), googleAndroidClientId: required('GOOGLE_ANDROID_CLIENT_ID') };
}
