export function getApiBase() {
  return import.meta.env.VITE_API_BASE_URL || (
    window.location.hostname === 'localhost' ? 'http://localhost:8081' : ''
  );
}

export function getApiUrl(path) {
  const base = getApiBase();
  return `${base}${path}`;
}

const SILENT_USER = () => import.meta.env.VITE_SILENT_AUTH_USER || 'local-user';
const SILENT_PASS = () => import.meta.env.VITE_SILENT_AUTH_PASS || 'local123';

export async function silentLogin() {
  try {
    const res = await fetch(getApiUrl('/api/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: SILENT_USER(), password: SILENT_PASS() }),
    });
    if (res.ok) {
      const data = await res.json();
      localStorage.setItem('token', data.token);
      localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('username', data.username);
      return data.token;
    }
  } catch (err) {
    console.error('silentLogin failed:', err);
  }
  return null;
}

/**
 * Drop-in replacement for fetch() that automatically:
 * - Injects the current JWT from localStorage as Authorization: Bearer
 * - On 401/403, silently re-authenticates once and retries
 */
export async function authFetch(url, options = {}) {
  let currentToken = localStorage.getItem('token') || '';

  const buildOptions = (tok) => {
    const headers = { ...(options.headers || {}) };
    if (tok && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${tok}`;
    }
    return {
      ...options,
      headers
    };
  };

  let res = await fetch(url, buildOptions(currentToken));

  if (res.status === 401 || res.status === 403) {
    const newToken = await silentLogin();
    if (newToken) {
      currentToken = newToken;
      res = await fetch(url, buildOptions(currentToken));
    }
  }

  return res;
}

export function getActiveLlmConfig() {
  try {
    const raw = localStorage.getItem('rag_llm_config');
    if (raw) return JSON.parse(raw);
  } catch (e) {
    console.error('Failed to parse active LLM config', e);
  }
  return {
    provider: 'OPENAI',
    model: 'gpt-4o-mini',
    apiKey: '',
    baseUrl: 'https://api.openai.com/v1',
    temperature: 0.2
  };
}

export function saveActiveLlmConfig(config) {
  localStorage.setItem('rag_llm_config', JSON.stringify(config));
}

export async function testLlmConnection(config, token) {
  const res = await authFetch(getApiUrl('/api/llm/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ config })
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `HTTP ${res.status}`);
  }
  return await res.json();
}

export async function getLlmPresets(token) {
  const res = await authFetch(getApiUrl('/api/llm/presets'));
  if (!res.ok) {
    throw new Error(`Failed to fetch presets: HTTP ${res.status}`);
  }
  return await res.json();
}