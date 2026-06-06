import { describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/msw/server';
import { apiClient, errorMessage, setUnauthorizedHandler } from './client';
import { getToken, setToken } from '../auth/tokenStore';

describe('apiClient interceptors', () => {
  it('attaches the bearer token to requests', async () => {
    setToken('tok-123');
    let authHeader: string | null = null;
    server.use(
      http.get('http://api.test/ping', ({ request }) => {
        authHeader = request.headers.get('authorization');
        return HttpResponse.json({ ok: true });
      }),
    );

    const res = await apiClient.get('http://api.test/ping');
    expect(res.data).toEqual({ ok: true });
    expect(authHeader).toBe('Bearer tok-123');
  });

  it('sends no Authorization header when there is no token', async () => {
    let authHeader: string | null = 'unset';
    server.use(
      http.get('http://api.test/anon', ({ request }) => {
        authHeader = request.headers.get('authorization');
        return HttpResponse.json({ ok: true });
      }),
    );

    await apiClient.get('http://api.test/anon');
    expect(authHeader).toBeNull();
  });

  it('on 401 clears the token and invokes the unauthorized handler', async () => {
    setToken('tok-123');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    server.use(http.get('http://api.test/secure', () => new HttpResponse(null, { status: 401 })));

    await expect(apiClient.get('http://api.test/secure')).rejects.toBeDefined();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();

    setUnauthorizedHandler(() => {});
  });

  it('extracts the backend message from an axios error', async () => {
    server.use(
      http.get('http://api.test/bad', () =>
        HttpResponse.json({ status: 409, error: 'Conflict', message: 'duplicate reference' }, { status: 409 }),
      ),
    );

    try {
      await apiClient.get('http://api.test/bad');
      throw new Error('should have thrown');
    } catch (err) {
      expect(errorMessage(err)).toBe('duplicate reference');
    }
  });
});
