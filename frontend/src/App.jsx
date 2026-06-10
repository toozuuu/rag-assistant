import React, { useState, useEffect } from 'react';
import ChatWindow from './components/ChatWindow';
import FileUpload from './components/FileUpload';
import DocumentWriter from './components/DocumentWriter';
import { getApiUrl } from './api';
import './index.css';

import { motion, AnimatePresence } from 'motion/react';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [username, setUsername] = useState(localStorage.getItem('username') || 'local-user');
  const [activeTab, setActiveTab] = useState('chat');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  
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
      const response = await fetch(getApiUrl('/api/auth/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'local-user', password: '' })
      });

      if (response.ok) {
        const data = await response.json();
        localStorage.setItem('token', data.token);
        localStorage.setItem('username', 'local-user');
        setToken(data.token);
        setUsername('local-user');
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

  const handleSelectWorkspace = (name) => {
    setCurrentWorkspace(name);
    localStorage.setItem('rag_current_workspace', name);
    setSidebarOpen(false); // Close drawer on selection
  };

  const handleCreateWorkspace = (e) => {
    e.preventDefault();
    const cleanName = newWorkspaceName.trim();
    if (!cleanName || workspaces.includes(cleanName)) return;

    if (!/^[a-zA-Z0-9_-]+$/.test(cleanName)) {
      alert('Workspace name can only contain letters, numbers, hyphens, and underscores.');
      return;
    }
    if (cleanName.length > 50) {
      alert('Workspace name must be 50 characters or fewer.');
      return;
    }

    const updated = [...workspaces, cleanName];
    setWorkspaces(updated);
    localStorage.setItem('rag_workspaces', JSON.stringify(updated));
    setCurrentWorkspace(cleanName);
    localStorage.setItem('rag_current_workspace', cleanName);
    setNewWorkspaceName('');
  };

  const handleRemoveWorkspace = (name, e) => {
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

  return (
    <div className="portal-container animate-fade-in">
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
              className={`workspace-item glass ${currentWorkspace === ws ? 'active' : ''}`}
              onClick={() => handleSelectWorkspace(ws)}
              title={ws}
            >
              <span className="workspace-name">{ws}</span>
              {ws !== 'default' && (
                <button
                  className="remove-ws-btn"
                  onClick={(e) => handleRemoveWorkspace(ws, e)}
                  title={`Delete ${ws} workspace`}
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
      </aside>

      {/* Main Grounded Assistant Content */}
      <div className="app-container">
        <header className="app-header glass">
          <div className="header-meta-group">
            {/* Hamburger button on mobile */}
            <button className="menu-toggle-btn" onClick={() => setSidebarOpen(true)}>
              ☰
            </button>
            <div className="header-meta">
              <h1>Knowledge Portal <span className="version-badge">v1.0.0</span></h1>
              <p>Secure enterprise document ingestion & contextual intelligence.</p>
            </div>
          </div>

          <div className="tab-navigation glass">
            <button 
              id="header-tab-chat"
              className={`tab-btn ${activeTab === 'chat' ? 'active' : ''}`}
              onClick={() => setActiveTab('chat')}
            >
              Chat Assistant
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

          <div className="user-badge glass" onClick={() => setSidebarOpen(true)}>
            <span className="user-icon pulse-online"></span>
            <span className="username">Pool: {currentWorkspace}</span>
          </div>
        </header>

        <div className="main-content">
          {/* FileUpload: Left-side on desktop, swapped tab on mobile */}
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
                className="mobile-only-upload"
                style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%', minHeight: 0 }}
              >
                <FileUpload token={token} workspace={currentWorkspace} onAuthError={fetchTokenSilently} />
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Mobile Bottom Navigation Bar */}
        <nav className="mobile-nav-bar glass">
          <button 
            id="mobile-nav-chat"
            className={`mobile-nav-btn ${activeTab === 'chat' ? 'active' : ''}`}
            onClick={() => setActiveTab('chat')}
          >
            <span className="nav-label">Chat</span>
          </button>
          <button 
            id="mobile-nav-writer"
            className={`mobile-nav-btn ${activeTab === 'writer' ? 'active' : ''}`}
            onClick={() => setActiveTab('writer')}
          >
            <span className="nav-label">Writer</span>
          </button>
          <button 
            id="mobile-nav-files"
            className={`mobile-nav-btn ${activeTab === 'files' ? 'active' : ''}`}
            onClick={() => setActiveTab('files')}
          >
            <span className="nav-label">Files</span>
          </button>
        </nav>
      </div>
    </div>
  );
}

export default App;
