import { useState, useEffect } from 'react';
import ChatWindow from './components/ChatWindow';
import FileUpload from './components/FileUpload';
import DocumentWriter from './components/DocumentWriter';
import RepoConnectModal from './components/RepoConnectModal';
import LlmSettingsModal from './components/LlmSettingsModal';
import { getApiUrl, getActiveLlmConfig } from './api';
import './index.css';

import { motion, AnimatePresence } from 'motion/react';

const getProviderIcon = (provider) => {
  switch (provider?.toUpperCase()) {
    case 'ANTHROPIC': return '🧠';
    case 'OLLAMA': return '🦙';
    case 'OPENROUTER': return '🌐';
    case 'GROQ': return '⚡';
    case 'CUSTOM': return '⚙️';
    default: return '⚡';
  }
};

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [activeTab, setActiveTab] = useState('chat');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem('rag_theme') || 'dark');
  const [repoModalOpen, setRepoModalOpen] = useState(false);
  const [llmModalOpen, setLlmModalOpen] = useState(false);
  const [activeLlmConfig, setActiveLlmConfig] = useState(() => getActiveLlmConfig());
  
  const [workspaces, setWorkspaces] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('rag_workspaces'));
      return Array.isArray(saved) && saved.length > 0 ? saved : ['default'];
    } catch {
      return ['default'];
    }
  });

  const [currentWorkspace, setCurrentWorkspace] = useState(() => {
    return localStorage.getItem('rag_current_workspace') || 'default';
  });

  const [newWorkspaceName, setNewWorkspaceName] = useState('');

  const fetchTokenSilently = async () => {
    try {
      const silentUser = import.meta.env.VITE_SILENT_AUTH_USER || 'local-user';
      const silentPass = import.meta.env.VITE_SILENT_AUTH_PASS || 'local123';
      const response = await fetch(getApiUrl('/api/auth/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: silentUser, password: silentPass })
      });

      if (response.ok) {
        const data = await response.json();
        localStorage.setItem('token', data.token);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('username', data.username);
        setToken(data.token);
        return data.token;
      }
    } catch (err) {
      console.error("Silent background authentication failed:", err);
    }
    return null;
  };

  useEffect(() => {
    fetchTokenSilently();
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('rag_theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  const handleSelectWorkspace = (name) => {
    setCurrentWorkspace(name);
    localStorage.setItem('rag_current_workspace', name);
    setSidebarOpen(false);
  };

  const handleCreateWorkspace = (e) => {
    e.preventDefault();
    const name = newWorkspaceName.trim().toLowerCase().replace(/[^a-z0-9-_]/g, '');
    if (!name) return;

    if (!workspaces.includes(name)) {
      const updated = [...workspaces, name];
      setWorkspaces(updated);
      localStorage.setItem('rag_workspaces', JSON.stringify(updated));
    }
    setCurrentWorkspace(name);
    localStorage.setItem('rag_current_workspace', name);
    setNewWorkspaceName('');
    setSidebarOpen(false);
  };

  const handleRemoveWorkspace = (e, name) => {
    e.stopPropagation();
    if (name === 'default') return;

    const updated = workspaces.filter(ws => ws !== name);
    setWorkspaces(updated);
    localStorage.setItem('rag_workspaces', JSON.stringify(updated));
    localStorage.removeItem(`rag_indexed_files_${name}`);

    if (currentWorkspace === name) {
      setCurrentWorkspace('default');
      localStorage.setItem('rag_current_workspace', 'default');
    }
  };

  const handleRepoIndexed = (workspaceName) => {
    if (!workspaces.includes(workspaceName)) {
      const updated = [...workspaces, workspaceName];
      setWorkspaces(updated);
      localStorage.setItem('rag_workspaces', JSON.stringify(updated));
    }
    setCurrentWorkspace(workspaceName);
    localStorage.setItem('rag_current_workspace', workspaceName);
    setActiveTab('chat');
  };

  return (
    <div className="portal-container animate-fade-in">
      {/* Repo Connect Modal */}
      <RepoConnectModal
        token={token}
        onAuthError={fetchTokenSilently}
        isOpen={repoModalOpen}
        onClose={() => setRepoModalOpen(false)}
        onRepoIndexed={handleRepoIndexed}
      />

      {/* LLM Configuration Settings Modal */}
      <LlmSettingsModal
        isOpen={llmModalOpen}
        onClose={() => setLlmModalOpen(false)}
        token={token}
        onConfigSaved={(config) => setActiveLlmConfig(config)}
      />

      {/* Mobile Drawer Backdrop */}
      {sidebarOpen && (
        <div className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Workspace Sidebar Switcher */}
      <aside className={`sidebar-panel glass ${sidebarOpen ? 'open' : ''}`}>
        <div className="sidebar-header">
          <h2>Workspaces</h2>
          <button className="close-sidebar-btn" onClick={() => setSidebarOpen(false)}>✕</button>
        </div>

        <div className="workspace-list">
          {workspaces.map(ws => (
            <div
              key={ws}
              className={`workspace-item ${currentWorkspace === ws ? 'active' : ''}`}
              onClick={() => handleSelectWorkspace(ws)}
            >
              <span className="ws-dot">📁</span>
              <span className="workspace-name">{ws}</span>
              {ws !== 'default' && (
                <button
                  className="remove-ws-btn"
                  onClick={(e) => handleRemoveWorkspace(e, ws)}
                  title="Remove workspace"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>

        <form onSubmit={handleCreateWorkspace} className="create-workspace-form">
          <input
            type="text"
            placeholder="+ New workspace..."
            value={newWorkspaceName}
            onChange={(e) => setNewWorkspaceName(e.target.value)}
            maxLength={20}
            required
          />
          {newWorkspaceName.trim() && (
            <button type="submit" className="create-ws-btn">Add</button>
          )}
        </form>

        <div style={{ marginTop: '1rem', padding: '0 0.5rem' }}>
          <button
            className="connect-repo-sidebar-btn"
            onClick={() => { setSidebarOpen(false); setRepoModalOpen(true); }}
          >
            <span>🐙</span>
            <span>Connect Git Repo</span>
          </button>
        </div>
      </aside>

      {/* Main Grounded Assistant Content */}
      <div className="app-container">
        <header className="app-header glass">
          <div className="header-meta-group">
            <button className="menu-toggle-btn" onClick={() => setSidebarOpen(true)}>
              ☰
            </button>
            <div className="header-meta">
              <h1>Knowledge Portal <span className="version-badge">v1.0.0</span></h1>
              <p>Enterprise document & codebase QA requirement intelligence.</p>
            </div>
          </div>

          <div className="tab-navigation glass">
            <button 
              id="header-tab-chat"
              className={`tab-btn ${activeTab === 'chat' ? 'active' : ''}`}
              onClick={() => setActiveTab('chat')}
            >
              QA & Chat Assistant
            </button>
            <button 
              id="header-tab-writer"
              className={`tab-btn ${activeTab === 'writer' ? 'active' : ''}`}
              onClick={() => setActiveTab('writer')}
            >
              Document Writer
            </button>
            <button 
              id="header-tab-files"
              className={`tab-btn mobile-files-tab-btn ${activeTab === 'files' ? 'active' : ''}`}
              onClick={() => setActiveTab('files')}
            >
              Document Hub
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
            <button
              className="llm-model-badge-btn glass"
              onClick={() => setLlmModalOpen(true)}
              title="Configure AI LLM Model (OpenAI, Claude, Ollama, Groq, etc.)"
            >
              <span>{getProviderIcon(activeLlmConfig.provider)}</span>
              <span>{activeLlmConfig.model || 'AI Model'}</span>
            </button>

            <button
              className="connect-repo-header-btn"
              onClick={() => setRepoModalOpen(true)}
              title="Connect GitHub or Bitbucket codebase"
            >
              <span>🐙</span>
              <span>Connect Repo</span>
            </button>

            <button
              className="theme-toggle-btn glass"
              onClick={toggleTheme}
              title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>
            <div className="user-badge glass" onClick={() => setSidebarOpen(true)}>
              <span className="user-icon pulse-online"></span>
              <span className="username">Pool: {currentWorkspace}</span>
            </div>
          </div>
        </header>

        <div className="main-content">
          <div className="desktop-only-upload">
            <FileUpload token={token} workspace={currentWorkspace} onAuthError={fetchTokenSilently} />
          </div>

          <AnimatePresence mode="wait">
            {activeTab === 'chat' && (
              <motion.div
                key="chat"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
                style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%', minHeight: 0 }}
              >
                <ChatWindow token={token} workspace={currentWorkspace} onAuthError={fetchTokenSilently} />
              </motion.div>
            )}
            {activeTab === 'writer' && (
              <motion.div
                key="writer"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
                style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%', minHeight: 0 }}
              >
                <DocumentWriter token={token} workspace={currentWorkspace} onAuthError={fetchTokenSilently} />
              </motion.div>
            )}
            {activeTab === 'files' && (
              <motion.div
                key="files"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
                style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%', minHeight: 0 }}
              >
                <div className="mobile-only-upload">
                  <FileUpload token={token} workspace={currentWorkspace} onAuthError={fetchTokenSilently} />
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Floating Mobile Bottom Navigation */}
        <nav className="mobile-nav-bar glass">
          <button
            className={`mobile-nav-btn ${activeTab === 'chat' ? 'active' : ''}`}
            onClick={() => setActiveTab('chat')}
          >
            💬 Chat
          </button>
          <button
            className={`mobile-nav-btn ${activeTab === 'writer' ? 'active' : ''}`}
            onClick={() => setActiveTab('writer')}
          >
            ✍️ Writer
          </button>
          <button
            className={`mobile-nav-btn ${activeTab === 'files' ? 'active' : ''}`}
            onClick={() => setActiveTab('files')}
          >
            📁 Files
          </button>
          <button
            className="mobile-nav-btn"
            onClick={() => setSidebarOpen(true)}
          >
            ☰ Pools
          </button>
        </nav>
      </div>
    </div>
  );
}

export default App;