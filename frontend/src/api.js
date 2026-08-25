export function getApiBase() {
  return import.meta.env.VITE_API_BASE_URL || (
    window.location.hostname === 'localhost' ? 'http://localhost:8081' : ''
  );
}

export function getApiUrl(path) {
  const base = getApiBase();
  return `${base}${path}`;
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
  const res = await fetch(getApiUrl('/api/llm/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
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
  const res = await fetch(getApiUrl('/api/llm/presets'), {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch presets: HTTP ${res.status}`);
  }
  return await res.json();
}