import React, { useState, useEffect } from 'react';
import { getActiveLlmConfig, saveActiveLlmConfig, testLlmConnection, getLlmPresets } from '../api';
import './LlmSettingsModal.css';

const DEFAULT_PROVIDERS = [
  {
    id: 'OPENAI',
    name: 'OpenAI',
    icon: '⚡',
    defaultBaseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o-mini',
    requiresApiKey: true,
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'o1-preview', 'o1-mini']
  },
  {
    id: 'ANTHROPIC',
    name: 'Anthropic Claude',
    icon: '🧠',
    defaultBaseUrl: 'https://api.anthropic.com/v1',
    defaultModel: 'claude-3-5-sonnet-20241022',
    requiresApiKey: true,
    models: ['claude-3-5-sonnet-20241022', 'claude-3-5-haiku-20241022', 'claude-3-opus-20240229']
  },
  {
    id: 'OLLAMA',
    name: 'Ollama (Local)',
    icon: '🦙',
    defaultBaseUrl: 'http://localhost:11434',
    defaultModel: 'phi3:mini',
    requiresApiKey: false,
    models: ['phi3:mini', 'llama3.1', 'llama3.2', 'qwen2.5-coder', 'mistral', 'deepseek-r1', 'nomic-embed-text']
  },
  {
    id: 'OPENROUTER',
    name: 'OpenRouter',
    icon: '🌐',
    defaultBaseUrl: 'https://openrouter.ai/api/v1',
    defaultModel: 'anthropic/claude-3.5-sonnet',
    requiresApiKey: true,
    models: ['anthropic/claude-3.5-sonnet', 'openai/gpt-4o', 'meta-llama/llama-3.1-70b-instruct', 'deepseek/deepseek-r1', 'google/gemini-2.0-flash-exp:free']
  },
  {
    id: 'GROQ',
    name: 'Groq Cloud',
    icon: '⚡',
    defaultBaseUrl: 'https://api.groq.com/openai/v1',
    defaultModel: 'llama-3.3-70b-versatile',
    requiresApiKey: true,
    models: ['llama-3.3-70b-versatile', 'llama-3.1-8b-instant', 'mixtral-8x7b-32768', 'gemma2-9b-it']
  },
  {
    id: 'CUSTOM',
    name: 'Custom Endpoint',
    icon: '⚙️',
    defaultBaseUrl: 'http://localhost:8000/v1',
    defaultModel: 'custom-model',
    requiresApiKey: false,
    models: []
  }
];

export default function LlmSettingsModal({ isOpen, onClose, token, onConfigSaved }) {
  const [providers, setProviders] = useState(DEFAULT_PROVIDERS);
  const [selectedProvider, setSelectedProvider] = useState('OPENAI');
  const [model, setModel] = useState('gpt-4o-mini');
  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('https://api.openai.com/v1');
  const [temperature, setTemperature] = useState(0.2);
  const [showKey, setShowKey] = useState(false);

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);

  useEffect(() => {
    if (isOpen) {
      const active = getActiveLlmConfig();
      if (active) {
        setSelectedProvider(active.provider || 'OPENAI');
        setModel(active.model || 'gpt-4o-mini');
        setApiKey(active.apiKey || '');
        setBaseUrl(active.baseUrl || 'https://api.openai.com/v1');
        setTemperature(active.temperature !== undefined ? active.temperature : 0.2);
      }
      setTestResult(null);

      // Fetch presets from backend if available
      if (token) {
        getLlmPresets(token)
          .then(data => {
            if (data?.providers?.length) {
              setProviders(data.providers);
            }
          })
          .catch(() => {});
      }
    }
  }, [isOpen, token]);

  if (!isOpen) return null;

  const currentProviderObj = providers.find(p => p.id === selectedProvider) || providers[0];

  const handleProviderSelect = (providerId) => {
    setSelectedProvider(providerId);
    const p = providers.find(item => item.id === providerId);
    if (p) {
      setModel(p.defaultModel || '');
      setBaseUrl(p.defaultBaseUrl || '');
    }
    setTestResult(null);
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);

    const config = {
      provider: selectedProvider,
      model: model.trim(),
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim(),
      temperature: parseFloat(temperature)
    };

    try {
      const res = await testLlmConnection(config, token);
      setTestResult(res);
    } catch (err) {
      setTestResult({
        success: false,
        errorMessage: err.message || 'Connection test failed.'
      });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = () => {
    const config = {
      provider: selectedProvider,
      model: model.trim() || currentProviderObj.defaultModel,
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim() || currentProviderObj.defaultBaseUrl,
      temperature: parseFloat(temperature)
    };

    saveActiveLlmConfig(config);
    if (onConfigSaved) {
      onConfigSaved(config);
    }
    onClose();
  };

  return (
    <div className="llm-modal-backdrop animate-fade-in" onClick={onClose}>
      <div className="llm-modal" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="llm-modal-header">
          <div className="llm-modal-title">
            <span className="llm-icon">🤖</span>
            <div>
              <h3>AI Model & LLM Configuration</h3>
              <p>Connect and configure your preferred AI engine (Claude, OpenAI, Ollama, Groq, etc.)</p>
            </div>
          </div>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {/* Provider Tabs */}
        <div className="provider-tabs-grid">
          {providers.map(p => (
            <button
              key={p.id}
              type="button"
              className={`provider-tab-btn ${selectedProvider === p.id ? 'active' : ''}`}
              onClick={() => handleProviderSelect(p.id)}
            >
              <span>{p.icon || '⚡'}</span>
              <span>{p.name}</span>
            </button>
          ))}
        </div>

        {/* Form Body */}
        <div className="llm-form-body">
          {/* Model Selection & Presets */}
          <div className="form-group">
            <label>Model Name</label>
            <input
              type="text"
              value={model}
              onChange={e => setModel(e.target.value)}
              placeholder="e.g. gpt-4o, claude-3-5-sonnet-20241022, phi3:mini"
              required
            />
            {currentProviderObj?.models?.length > 0 && (
              <div className="model-presets">
                <span className="presets-label">Popular Presets:</span>
                <div className="preset-chips">
                  {currentProviderObj.models.map(m => (
                    <button
                      key={m}
                      type="button"
                      className={`preset-chip ${model === m ? 'selected' : ''}`}
                      onClick={() => setModel(m)}
                    >
                      {m}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* API Key */}
          <div className="form-group">
            <div className="label-row">
              <label>API Key {currentProviderObj.requiresApiKey ? '(Required)' : '(Optional for Local Ollama)'}</label>
              <button
                type="button"
                className="toggle-key-btn"
                onClick={() => setShowKey(!showKey)}
              >
                {showKey ? '🔒 Hide' : '👁️ Show'}
              </button>
            </div>
            <input
              type={showKey ? 'text' : 'password'}
              value={apiKey}
              onChange={e => setApiKey(e.target.value)}
              placeholder={currentProviderObj.requiresApiKey ? 'sk-...' : 'Leave empty for local Ollama / custom proxy'}
            />
          </div>

          {/* Base URL */}
          <div className="form-group">
            <label>Endpoint / Base URL</label>
            <input
              type="text"
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              placeholder="https://..."
            />
          </div>

          {/* Temperature Slider */}
          <div className="form-group">
            <div className="label-row">
              <label>Temperature (Creativity): {temperature}</label>
              <span className="temp-hint">{temperature < 0.3 ? 'Strict & Grounded' : temperature > 0.7 ? 'Creative' : 'Balanced'}</span>
            </div>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={temperature}
              onChange={e => setTemperature(parseFloat(e.target.value))}
              className="temp-slider"
            />
          </div>

          {/* Live Test Feedback Banner */}
          {testResult && (
            <div className={`test-result-banner ${testResult.success ? 'success' : 'error'}`}>
              <div className="result-header">
                <span>{testResult.success ? '✓ Connection Verified' : '✕ Connection Failed'}</span>
                {testResult.latencyMs > 0 && (
                  <span className="latency-badge">{testResult.latencyMs}ms</span>
                )}
              </div>
              <p className="result-detail">
                {testResult.success ? testResult.response : testResult.errorMessage}
              </p>
            </div>
          )}
        </div>

        {/* Modal Actions */}
        <div className="llm-modal-actions">
          <button
            type="button"
            className="test-btn"
            onClick={handleTestConnection}
            disabled={testing}
          >
            {testing ? (
              <span className="spinner-wrap">
                <span className="btn-spinner" />
                <span>Testing...</span>
              </span>
            ) : (
              '🧪 Test Connection'
            )}
          </button>

          <div className="right-actions">
            <button type="button" className="cancel-btn" onClick={onClose}>
              Cancel
            </button>
            <button type="button" className="save-btn" onClick={handleSave}>
              💾 Save & Activate
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}