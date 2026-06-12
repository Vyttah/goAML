import { describe, expect, it } from 'vitest';
import { WARNING_LEAD_MS, msUntilWarning, shouldWarnNow } from './expiryWarning';
import { decodeJwt } from './jwt';
import { makeToken } from '../test/util';

describe('expiryWarning helpers', () => {
  const nowSec = 1_000_000; // fixed epoch seconds for deterministic math
  const nowMs = nowSec * 1000;

  it('schedules the warning at exp - lead while still in the future', () => {
    // exp 10 min out → warn 8 min out (10min - 2min lead)
    const claims = decodeJwt(makeToken({ exp: nowSec + 600 }));
    const delay = msUntilWarning(claims, nowMs);
    expect(delay).toBe(600 * 1000 - WARNING_LEAD_MS);
  });

  it('fires immediately (delay 0) when already inside the warning window', () => {
    // exp 1 min out, lead is 2 min → already inside the window
    const claims = decodeJwt(makeToken({ exp: nowSec + 60 }));
    expect(msUntilWarning(claims, nowMs)).toBe(0);
  });

  it('returns null when there is no usable exp', () => {
    expect(msUntilWarning(null, nowMs)).toBeNull();
    const noExp = decodeJwt(makeToken({ exp: undefined }));
    expect(msUntilWarning(noExp, nowMs)).toBeNull();
  });

  it('shouldWarnNow is true inside the window for a near-expiry token', () => {
    const nearExpiry = decodeJwt(makeToken({ exp: nowSec + 60 })); // 1 min out
    expect(shouldWarnNow(nearExpiry, nowMs)).toBe(true);
  });

  it('shouldWarnNow is false for a fresh token and for an already-expired token', () => {
    const fresh = decodeJwt(makeToken({ exp: nowSec + 3600 }));
    expect(shouldWarnNow(fresh, nowMs)).toBe(false);

    const expired = decodeJwt(makeToken({ exp: nowSec - 10 }));
    expect(shouldWarnNow(expired, nowMs)).toBe(false);
  });
});
