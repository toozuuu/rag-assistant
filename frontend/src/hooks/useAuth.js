import { useState, useCallback, useEffect } from 'react';
import { silentLogin } from '../api';

function loadToken() {
  return localStorage.getItem('token') || '';
}

export function useAuth() {
  const [token, setToken] = useState(loadToken);

  const doLogin = useCallback(async () => {
    const newToken = await silentLogin();
    if (newToken) {
      setToken(newToken);
      return newToken;
    }
    return null;
  }, []);

  // Silent login on mount
  useEffect(() => {
    let cancelled = false;
    doLogin().then(t => {
      if (!cancelled && t) setToken(t);
    });
    return () => { cancelled = true; };
  }, [doLogin]);

  return { token, refreshToken: doLogin };
}