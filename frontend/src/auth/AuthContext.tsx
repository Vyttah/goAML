import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { getToken, setToken, clearToken } from './tokenStore';
import { identityFromToken, type Identity } from './jwt';
import { hasAnyRole, type Role } from './roles';
import { setUnauthorizedHandler } from '../api/client';

interface AuthContextValue {
  identity: Identity | null;
  isAuthenticated: boolean;
  /** Persist the access token and adopt its identity (called by the login flow). */
  signIn: (accessToken: string) => void;
  /** Drop the token + identity (logout, or driven by a 401). */
  signOut: () => void;
  /** True when the current identity holds at least one of the given roles. */
  can: (...allowed: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<Identity | null>(() => identityFromToken(getToken()));

  const signIn = useCallback((accessToken: string) => {
    setToken(accessToken);
    setIdentity(identityFromToken(accessToken));
  }, []);

  const signOut = useCallback(() => {
    clearToken();
    setIdentity(null);
  }, []);

  // A 401 from any request clears the token (in the interceptor) and the in-app identity here, which
  // flips RequireAuth into a redirect-to-login. No refresh token exists, so 401 == re-login.
  useEffect(() => {
    setUnauthorizedHandler(() => setIdentity(null));
    return () => setUnauthorizedHandler(() => {});
  }, []);

  const can = useCallback(
    (...allowed: Role[]) => (identity ? hasAnyRole(identity.roles, allowed) : false),
    [identity],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ identity, isAuthenticated: identity !== null, signIn, signOut, can }),
    [identity, signIn, signOut, can],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>');
  }
  return ctx;
}
