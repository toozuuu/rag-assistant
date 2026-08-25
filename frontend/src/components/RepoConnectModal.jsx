import { useState } from 'react';
import { motion } from 'motion/react';
import { getApiUrl, authFetch } from '../api';
import './RepoConnectModal.css';

const RepoConnectModal = ({ token, onAuthError, isOpen, onClose, onRepoIndexed }) => {
  const [provider, setProvider] = useState('GITHUB');
  const [repoUrl, setRepoUrl] = useState('');
  const [branch, setBranch] = useState('main');
  const [username, setUsername] = useState('');
  const [tokenOrPassword, setTokenOrPassword] = useState('');
  const [workspace, setWorkspace] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  if (!isOpen) return null;

  const handleProviderSelect = (prov) => {
    setProvider(prov);
    if (!repoUrl) {
      if (prov === 'GITHUB') setRepoUrl('https://github.com/');
      else if (prov === 'BITBUCKET') setRepoUrl('https://bitbucket.org/');
      else if (prov === 'GITLAB') setRepoUrl('https://gitlab.com/');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!repoUrl.trim() || !workspace.trim()) {
      setError('Repository URL and Workspace name are required.');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    const payload = {
      repoUrl: repoUrl.trim(),
      branch: branch.trim() || 'main',
      provider,
      username: username.trim() || null,
      tokenOrPassword: tokenOrPassword.trim() || null,
      workspace: workspace.trim()
    };

    try {
      const res = await authFetch(getApiUrl('/api/repository/connect'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (res.ok && data.status === 'SUCCESS') {
        setResult(data);
        if (onRepoIndexed) {
          onRepoIndexed(workspace.trim(), data);
        }
      } else {
        setError(data.message || 'Failed to index repository. Check URL and access tokens.');
      }
    } catch (err) {
      setError('Network error connecting to repository backend: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="repo-modal-backdrop" onClick={onClose}>
      <motion.div 
        className="repo-modal glass"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
      >
        <div className="repo-modal-header">
          <div className="repo-modal-title">
            <span className="repo-icon">🐙</span>
            <div>
              <h3>Connect Code Repository</h3>
              <p>Connect GitHub, Bitbucket, or GitLab for QA requirement analysis & test generation.</p>
            </div>
          </div>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {/* Provider Selector */}
        <div className="provider-tabs">
          {[
            { id: 'GITHUB', label: 'GitHub', icon: '🐙' },
            { id: 'BITBUCKET', label: 'Bitbucket', icon: '🪣' },
            { id: 'GITLAB', label: 'GitLab', icon: '🦊' },
            { id: 'CUSTOM', label: 'Custom Git', icon: '🔗' },
          ].map((item) => (
            <button
              key={item.id}
              type="button"
              className={`provider-tab-btn ${provider === item.id ? 'active' : ''}`}
              onClick={() => handleProviderSelect(item.id)}
            >
              <span>{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="repo-form">
          <div className="form-group">
            <label>Repository HTTPS URL *</label>
            <input
              type="url"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder={
                provider === 'GITHUB' ? 'https://github.com/owner/repository' :
                provider === 'BITBUCKET' ? 'https://bitbucket.org/workspace/repository' :
                provider === 'GITLAB' ? 'https://gitlab.com/group/project' :
                'https://git.domain.com/repo.git'
              }
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group flex-1">
              <label>Branch / Tag</label>
              <input
                type="text"
                value={branch}
                onChange={(e) => setBranch(e.target.value)}
                placeholder="main / master / develop"
              />
            </div>
            <div className="form-group flex-1">
              <label>Target Workspace Name *</label>
              <input
                type="text"
                value={workspace}
                onChange={(e) => setWorkspace(e.target.value)}
                placeholder="e.g. backend-repo or qa-auth"
                required
              />
            </div>
          </div>

          <div className="auth-section glass">
            <h4>🔐 Authentication (Private Repositories)</h4>
            <p className="auth-hint">
              {provider === 'GITHUB' && 'Provide a GitHub Personal Access Token (classic or fine-grained with repo read access). Leave blank if public.'}
              {provider === 'BITBUCKET' && 'Provide Bitbucket Username & App Password (with Read Repositories permission).'}
              {provider === 'GITLAB' && 'Provide GitLab Personal Access Token (with read_repository scope).'}
              {provider === 'CUSTOM' && 'Provide HTTP Basic authentication token or password.'}
            </p>

            <div className="form-row">
              {provider === 'BITBUCKET' && (
                <div className="form-group flex-1">
                  <label>Bitbucket Username</label>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="e.g. your-bitbucket-username"
                  />
                </div>
              )}
              <div className="form-group flex-2">
                <label>Access Token / App Password</label>
                <input
                  type="password"
                  value={tokenOrPassword}
                  onChange={(e) => setTokenOrPassword(e.target.value)}
                  placeholder="ghp_xxxx / App Password / PAT..."
                />
              </div>
            </div>
          </div>

          {error && (
            <div className="repo-error-banner animate-fade-in">
              <span>⚠️</span>
              <span>{error}</span>
            </div>
          )}

          {result && (
            <div className="repo-success-banner animate-fade-in">
              <div className="success-header">
                <span>✅</span>
                <strong>{result.message}</strong>
              </div>
              {result.fileTypes && Object.keys(result.fileTypes).length > 0 && (
                <div className="filetypes-breakdown">
                  <span>Languages indexed:</span>
                  <div className="type-chips">
                    {Object.entries(result.fileTypes).map(([ext, count]) => (
                      <span key={ext} className="type-chip">
                        .{ext} ({count})
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="modal-actions">
            <button type="button" className="cancel-btn" onClick={onClose} disabled={loading}>
              {result ? 'Close' : 'Cancel'}
            </button>
            <button type="submit" className="connect-btn" disabled={loading}>
              {loading ? (
                <span className="spinner-wrap">
                  <span className="btn-spinner"></span> Cloning & Indexing Codebase...
                </span>
              ) : (
                'Connect & Index Codebase'
              )}
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
};

export default RepoConnectModal;