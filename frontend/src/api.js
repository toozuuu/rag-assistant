export function getApiBase() {
  return import.meta.env.VITE_API_BASE_URL || (
    window.location.hostname === 'localhost' ? 'http://localhost:8081' : ''
  );
}

export function getApiUrl(path) {
  const base = getApiBase();
  return `${base}${path}`;
}
