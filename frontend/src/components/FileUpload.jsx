import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { getApiUrl } from '../api';
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
  const [files, setFiles] = useState([]);
  const [dragging, setDragging] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const workspaceStorageKey = `rag_indexed_files_${workspace || 'default'}`;

  const fetchIndexedDocuments = async (currentToken) => {
    try {
      let res = await fetch(getApiUrl(`/api/documents?workspace=${encodeURIComponent(workspace || 'default')}`), {
        headers: { 'Authorization': `Bearer ${currentToken || token}` }
      });

      if ((res.status === 401 || res.status === 403) && onAuthError) {
        const newToken = await onAuthError();
        if (newToken) {
          res = await fetch(getApiUrl(`/api/documents?workspace=${encodeURIComponent(workspace || 'default')}`), {
            headers: { 'Authorization': `Bearer ${newToken}` }
          });
        }
      }

      if (res.ok) {
        const docs = await res.json();
        const mapped = docs.map(d => ({ name: d.filename, docId: d.docId, status: 'success' }));
        setFiles(prev => {
          const existing = prev.filter(f => f.file);
          const merged = [...existing, ...mapped];
          const seen = new Set();
          return merged.filter(f => { const key = f.name; if (seen.has(key)) return false; seen.add(key); return true; });
        });
      }
    } catch {
      // fallback to local storage
    }
  };

  // ── Load persisted indexed files when workspace changes ──────────────
  useEffect(() => {
    setFiles([]);
    fetchIndexedDocuments();
  }, [workspace]);

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

  const removeFile = async (name) => {
    const file = files.find(f => f.name === name);
    if (file?.docId) {
      setDeleting(name);
      try {
        const res = await fetch(getApiUrl(`/api/documents/${encodeURIComponent(file.docId)}?workspace=${encodeURIComponent(workspace || 'default')}`), {
          method: 'DELETE',
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
          console.error('Failed to delete document from backend');
        }
      } catch (e) {
        console.error('Error deleting document:', e);
      } finally {
        setDeleting(null);
      }
    }
    setFiles(prev => prev.filter(f => f.name !== name));
  };

  const clearAll = () => {
    if (!window.confirm('Remove all files from the list? This will also remove them from the vector database.')) return;
    setFiles([]);
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
      let currentToken = token;
      let res = await fetch(getApiUrl('/api/documents/upload'), {
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
            res = await fetch(getApiUrl('/api/documents/upload'), {
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

      if (res.ok) {
        setFiles(prev => prev.map(f =>
          f.name === item.name ? { ...f, status: 'success' } : f
        ));
        fetchIndexedDocuments(currentToken);
      } else {
        setFiles(prev => prev.map(f =>
          f.name === item.name ? { ...f, status: 'error' } : f
        ));
      }
    } catch {
      setFiles(prev => prev.map(f =>
        f.name === item.name ? { ...f, status: 'error' } : f
      ));
    }
  };

  const uploadAll = async () => {
    const toUpload = files.filter(f => f.status === 'pending' || f.status === 'error');
    await Promise.all(toUpload.map(uploadFile));
  };

  const pendingCount = files.filter(f => f.status === 'pending').length;
  const hasUploading  = files.some(f => f.status === 'uploading');
  const totalFiles = files.filter(f => f.file).length;
  const completedFiles = files.filter(f => f.file && (f.status === 'success' || f.status === 'error')).length;
  const uploadProgress = totalFiles > 0 ? Math.round((completedFiles / totalFiles) * 100) : 0;

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
                      disabled={deleting === name}
                    >{deleting === name ? '...' : '✕'}</button>
                  )}
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        </div>
      )}

      {/* Upload Progress */}
      {hasUploading && (
        <div className="progress-bar-container">
          <div className="progress-bar-fill" style={{ width: `${uploadProgress}%` }} />
          <div className="progress-text">{completedFiles} / {totalFiles} files indexed</div>
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
