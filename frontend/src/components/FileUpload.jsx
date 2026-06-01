import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import './FileUpload.css';

const FileIcon = ({ name }) => {
  const ext = name.split('.').pop().toUpperCase();
  return <span className="file-type-label">{ext}</span>;
};

const StatusBadge = ({ status }) => {
  const map = {
    pending:   { label: 'Ready',      cls: 'badge-pending' },
    uploading: { label: 'Indexing...', cls: 'badge-uploading' },
    success:   { label: '✓ Indexed',   cls: 'badge-success' },
    error:     { label: '✗ Failed',    cls: 'badge-error' },
  };
  const { label, cls } = map[status] || map.pending;
  return <span className={`status-badge ${cls}`}>{label}</span>;
};

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const FileUpload = ({ token, workspace, onAuthError }) => {
  // Each entry: { name, size, status, file? }
  // 'file' is only present for newly added (not yet persisted) entries
  const [files, setFiles] = useState([]);
  const [dragging, setDragging] = useState(false);

  const workspaceStorageKey = `rag_indexed_files_${workspace || 'default'}`;
  // ── Load persisted indexed files when workspace changes ──────────────
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(workspaceStorageKey) || '[]');
      // Saved entries have no File object, only metadata
      setFiles(saved.map(f => ({ ...f, file: null })));
    } catch {
      setFiles([]);
    }
  }, [workspace, workspaceStorageKey]);

  // ── Add new files (skip duplicates already in the list) ───────────
  const addFiles = (newFiles) => {
    const toAdd = Array.from(newFiles).filter(
      f => !files.some(existing => existing.name === f.name)
    );
    setFiles(prev => [
      ...prev,
      ...toAdd.map(f => ({ file: f, name: f.name, size: f.size, status: 'pending' })),
    ]);
  };

  const handleFileChange = (e) => {
    if (e.target.files) {
      addFiles(e.target.files);
    }
    e.target.value = '';
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    addFiles(e.dataTransfer.files);
  };

  const removeFile = (name) => {
    setFiles(prev => {
      const updated = prev.filter(f => f.name !== name);
      const toSave = updated
        .filter(f => f.status === 'success')
        .map(({ name, size, status }) => ({ name, size, status }));
      localStorage.setItem(workspaceStorageKey, JSON.stringify(toSave));
      return updated;
    });
  };

  const clearAll = () => {
    setFiles([]);
    localStorage.removeItem(workspaceStorageKey);
  };

  // ── Upload a single file entry ─────────────────────────────────────
  const uploadFile = async (item) => {
    if (!item.file) return; // persisted entries have no File object

    setFiles(prev => prev.map(f =>
      f.name === item.name ? { ...f, status: 'uploading' } : f
    ));

    const formData = new FormData();
    formData.append('file', item.file);
    formData.append('workspace', workspace || 'default');

    try {
      const apiBase = import.meta.env.VITE_API_BASE_URL || (window.location.hostname === 'localhost' ? 'http://localhost:8080' : '');
      let currentToken = token;
      let res = await fetch(`${apiBase}/api/documents/upload`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${currentToken}`
        },
        body: formData,
      });

      if (res.status === 401 || res.status === 403) {
        if (onAuthError) {
          const newToken = await onAuthError();
          if (newToken) {
            currentToken = newToken;
            res = await fetch(`${apiBase}/api/documents/upload`, {
              method: 'POST',
              headers: {
                'Authorization': `Bearer ${currentToken}`
              },
              body: formData,
            });
          }
        }
      }

      if (res.status === 401 || res.status === 403) {
        setFiles(prev => prev.map(f =>
          f.name === item.name ? { ...f, status: 'error' } : f
        ));
        return;
      }

      const status = res.ok ? 'success' : 'error';
      setFiles(prev => {
        const updated = prev.map(f =>
          f.name === item.name ? { ...f, status } : f
        );
        const toSave = updated
          .filter(f => f.status === 'success')
          .map(({ name, size, status }) => ({ name, size, status }));
        localStorage.setItem(workspaceStorageKey, JSON.stringify(toSave));
        return updated;
      });
    } catch {
      setFiles(prev => {
        const updated = prev.map(f =>
          f.name === item.name ? { ...f, status: 'error' } : f
        );
        const toSave = updated
          .filter(f => f.status === 'success')
          .map(({ name, size, status }) => ({ name, size, status }));
        localStorage.setItem(workspaceStorageKey, JSON.stringify(toSave));
        return updated;
      });
    }
  };

  const uploadAll = () =>
    files.filter(f => f.status === 'pending' || f.status === 'error').forEach(uploadFile);

  const pendingCount = files.filter(f => f.status === 'pending').length;
  const hasUploading  = files.some(f => f.status === 'uploading');

  return (
    <div className="file-upload-container glass">
      <h2>Document Hub</h2>
      <p>Securely ingest team resources, project guidelines, and platform documentations.</p>

      {/* Drop Zone */}
      <div
        className={`drop-zone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => document.getElementById('multi-file-input').click()}
      >
        <div className="drop-icon">↑</div>
        <p className="drop-text">
          Drag &amp; drop files here<br />
          <span>or click to browse</span>
        </p>
        <p className="drop-hint">PDF, TXT, DOCX, HTML supported</p>
        <input
          type="file"
          id="multi-file-input"
          multiple
          onChange={handleFileChange}
          accept=".pdf,.txt,.docx,.html,.md"
          style={{ display: 'none' }}
        />
      </div>

      {/* File List */}
      {files.length > 0 && (
        <div className="file-list">
          <div className="file-list-header">
            <span>
              {files.filter(f => f.status === 'success').length} indexed
              {pendingCount > 0 && ` · ${pendingCount} pending`}
            </span>
            <button className="clear-all-btn" onClick={clearAll}>Clear all</button>
          </div>

          <ul>
            <AnimatePresence initial={false}>
              {files.map(({ name, size, status }) => (
                <motion.li
                  key={name}
                  className={`file-item ${status}`}
                  initial={{ opacity: 0, height: 0, x: -15 }}
                  animate={{ opacity: 1, height: 'auto', x: 0 }}
                  exit={{ opacity: 0, height: 0, x: 15 }}
                  transition={{ duration: 0.2 }}
                  style={{ overflow: 'hidden' }}
                >
                  <FileIcon name={name} />
                  <div className="file-info">
                    <div className="file-name-row">
                      <span className="file-name" title={name}>{name}</span>
                    </div>
                    <div className="file-meta-row">
                      <span className="file-size">{formatSize(size)}</span>
                      <StatusBadge status={status} />
                    </div>
                  </div>
                  {status !== 'uploading' && (
                    <button
                      className="remove-btn"
                      onClick={() => removeFile(name)}
                      title="Remove"
                    >✕</button>
                  )}
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        </div>
      )}

      {/* Upload Button – only show when there are pending files with a File object */}
      {pendingCount > 0 && (
        <button
          className="upload-btn"
          onClick={uploadAll}
          disabled={hasUploading}
        >
          {hasUploading
            ? 'Indexing...'
            : `Upload & Index ${pendingCount} file${pendingCount !== 1 ? 's' : ''}`}
        </button>
      )}
    </div>
  );
};

export default FileUpload;
