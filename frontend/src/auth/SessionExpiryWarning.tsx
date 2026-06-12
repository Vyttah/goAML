import { useEffect } from 'react';
import { App } from 'antd';
import { useAuth } from './AuthContext';
import { getToken } from './tokenStore';
import { decodeJwt } from './jwt';
import { msUntilWarning } from './expiryWarning';

const NOTIFICATION_KEY = 'goaml-session-expiry';

/**
 * Non-blocking pre-expiry warning. The access token is 15 min with no refresh, so a few minutes
 * before it expires we surface a persistent AntD notification advising the user to save their work
 * and re-login — preventing a silent mid-task 401. Mounted under the app's AntApp + Auth providers;
 * the schedule resets whenever the identity (token) changes. The pure scheduling logic lives in
 * `expiryWarning.ts`.
 */
export function ExpiryWarning() {
  const { identity } = useAuth();
  const { notification } = App.useApp();

  useEffect(() => {
    if (!identity) {
      notification.destroy(NOTIFICATION_KEY);
      return;
    }
    const claims = decodeJwt(getToken());
    const delay = msUntilWarning(claims);
    if (delay === null) return;

    const fire = () =>
      notification.warning({
        key: NOTIFICATION_KEY,
        message: 'Your session is about to expire',
        description:
          'You will be signed out in about 2 minutes. Save or submit your work now, then sign in again to continue.',
        duration: 0,
        placement: 'topRight',
      });

    const timer = window.setTimeout(fire, delay);
    return () => window.clearTimeout(timer);
    // An identity change (new token) reschedules; the lead time is a module constant.
  }, [identity, notification]);

  return null;
}
