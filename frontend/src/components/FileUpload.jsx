import React, { useState, useEffect } from 'react';
import './FileUpload.css';

const STORAGE_KEY = 'rag_indexed_files';

const FileIcon = ({ name }) => {
  const ext = name.split('.').pop().toLowerCase();
  const icons = { pdf: '📄', docx: '📝', txt: '📃', html: '🌐', md: '📋' };
  return <span className="file-icon">{icons[ext] || '📁'}</span>;
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

const FileUpload = () => {
  // Each entry: { name, size, status, file? }
  // 'file' is only present for newly added (not yet persisted) entries
  const [files, setFiles] = useState([]);
  const [dragging, setDragging] = useState(false);

  // ── Load persisted indexed files on mount ──────────────────────────
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      // Saved entries have no File object, only metadata
      setFiles(saved.map(f => ({ ...f, file: null })));
    } catch {
      // ignore corrupt storage
    }
  }, []);

  // ── Persist indexed files whenever the list changes ────────────────
  useEffect(() => {
    const toSave = files
      .filter(f => f.status === 'success')
      .map(({ name, size, status }) => ({ name, size, status }));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave));
  }, [files]);

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

  const handleFileChange = (e) => addFiles(e.target.files);

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    addFiles(e.dataTransfer.files);
  };

  const removeFile = (name) =>
    setFiles(prev => prev.filter(f => f.name !== name));

  const clearAll = () => {
    setFiles([]);
    localStorage.removeItem(STORAGE_KEY);
  };

  // ── Upload a single file entry ─────────────────────────────────────
  const uploadFile = async (item) => {
    if (!item.file) return; // persisted entries have no File object

    setFiles(prev => prev.map(f =>
      f.name === item.name ? { ...f, status: 'uploading' } : f
    ));

    const formData = new FormData();
    formData.append('file', item.file);

    try {
      const res = await fetch('http://localhost:8080/api/documents/upload', {
        method: 'POST',
        body: formData,
      });
      const status = res.ok ? 'success' : 'error';
      setFiles(prev => prev.map(f =>
        f.name === item.name ? { ...f, status } : f
      ));
    } catch {
      setFiles(prev => prev.map(f =>
        f.name === item.name ? { ...f, status: 'error' } : f
      ));
    }
  };

  const uploadAll = () =>
    files.filter(f => f.status === 'pending' || f.status === 'error').forEach(uploadFile);

  const pendingCount = files.filter(f => f.status === 'pending').length;
  const hasUploading  = files.some(f => f.status === 'uploading');

  return (
    <div className="file-upload-container glass">
      <h2>📚 WMP Document Hub</h2>
      <p>Securely ingest team resources, project guidelines, and platform documentations for WMP LLC.</p>

      {/* Drop Zone */}
      <div
        className={`drop-zone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => document.getElementById('multi-file-input').click()}
      >
        <div className="drop-icon">☁️</div>
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
            {files.map(({ name, size, status }) => (
              <li key={name} className={`file-item ${status}`}>
                <FileIcon name={name} />
                <div className="file-info">
                  <span className="file-name" title={name}>{name}</span>
                  <span className="file-size">{formatSize(size)}</span>
                </div>
                <StatusBadge status={status} />
                {status !== 'uploading' && (
                  <button
                    className="remove-btn"
                    onClick={() => removeFile(name)}
                    title="Remove"
                  >✕</button>
                )}
              </li>
            ))}
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
