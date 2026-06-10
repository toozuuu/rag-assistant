import React, { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import { motion, AnimatePresence } from 'motion/react';
import { getApiUrl } from '../api';
import './DocumentWriter.css';

const DocumentWriter = ({ token, workspace, onAuthError }) => {
  const [prompt, setPrompt] = useState('');
  const [draft, setDraft] = useState('');
  const [reasoning, setReasoning] = useState('');
  const [loading, setLoading] = useState(false);
  const [editorMode, setEditorMode] = useState('split'); // 'split' | 'edit' | 'preview'
  const [copied, setCopied] = useState(false);
  const objectUrlRef = useRef(null);

  // Load template helper
  const handleSelectTemplate = (templateName) => {
    switch (templateName) {
      case 'software-spec':
        setPrompt("Draft a Software Design Specification document. Outline system architecture, technical dependencies, API design, and deployment stages based on files in this workspace.");
        break;
      case 'support-agreement':
        setPrompt("Draft a Software Support & Maintenance Agreement. Detail SLAs, severity levels, support channels, and response windows grounded in our guidelines in this workspace.");
        break;
      case 'project-plan':
        setPrompt("Draft a Project Deliverables & Roadmap Proposal. Create timelines, milestone charts, and resource allocations based on our project files.");
        break;
      case 'data-privacy':
        setPrompt("Draft a GDPR-compliant Data Privacy Policy outline. Specify data processing parameters, retention bounds, and user consent protocols using our policy templates.");
        break;
      default:
        break;
    }
  };

  const handleGenerate = async () => {
    if (!prompt.trim()) return;
    setLoading(true);
    setReasoning('');

    try {
      let currentToken = token;
      let response = await fetch(getApiUrl('/api/writer/generate'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${currentToken}`
        },
        body: JSON.stringify({ prompt, workspace: workspace || 'default' })
      });

      if (response.status === 401 || response.status === 403) {
        if (onAuthError) {
          const newToken = await onAuthError();
          if (newToken) {
            currentToken = newToken;
            response = await fetch(getApiUrl('/api/writer/generate'), {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
              },
              body: JSON.stringify({ prompt, workspace: workspace || 'default' })
            });
          }
        }
      }

      if (response.status === 401 || response.status === 403) {
        setDraft("# Authentication Error\nYour session has expired. Please verify your connection or try again.");
        return;
      }

      const data = await response.json();
      setDraft(data.draft || '');
      setReasoning(data.reasoning || '');
    } catch (error) {
      console.error("Document generation failed:", error);
      setDraft("# Error Generating Document\nWe encountered a connection issue while communicating with the server.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
      }
    };
  }, []);

  const handleCopy = () => {
    if (!draft) return;
    navigator.clipboard.writeText(draft);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    if (!draft) return;
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current);
    }
    const blob = new Blob([draft], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    objectUrlRef.current = url;
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${workspace || 'workspace'}_document_${Date.now()}.md`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handlePrint = () => {
    if (!draft) return;

    // Create a hidden iframe for print scope
    const iframe = document.createElement('iframe');
    iframe.style.position = 'absolute';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = 'none';
    document.body.appendChild(iframe);

    // Build styled document inside iframe
    const doc = iframe.contentWindow.document;
    doc.open();
    doc.write(`
      <html>
        <head>
          <title>${workspace || 'workspace'}_document</title>
          <style>
            @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&display=swap');
            body {
              font-family: 'Outfit', sans-serif;
              line-height: 1.6;
              color: #1e293b;
              padding: 3rem;
              background: #ffffff;
            }
            h1, h2, h3, h4 {
              color: #0f172a;
              margin-top: 2rem;
              margin-bottom: 1rem;
              font-weight: 600;
            }
            h1 { font-size: 2.2rem; border-bottom: 2px solid #e2e8f0; padding-bottom: 0.75rem; margin-top: 0; }
            h2 { font-size: 1.6rem; border-bottom: 1px solid #e2e8f0; padding-bottom: 0.5rem; }
            h3 { font-size: 1.3rem; }
            p { margin-bottom: 1.25rem; font-size: 1.05rem; }
            ul, ol { margin-bottom: 1.25rem; padding-left: 1.5rem; }
            li { margin-bottom: 0.5rem; }
            code {
              background: #f1f5f9;
              padding: 0.2rem 0.4rem;
              border-radius: 4px;
              font-family: monospace;
              font-size: 0.9em;
              color: #0f172a;
            }
            pre {
              background: #f1f5f9;
              padding: 1.25rem;
              border-radius: 8px;
              overflow-x: auto;
              border: 1px solid #e2e8f0;
              margin-bottom: 1.5rem;
            }
            pre code {
              background: none;
              padding: 0;
              color: inherit;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              margin: 2rem 0;
            }
            th, td {
              border: 1px solid #cbd5e1;
              padding: 0.75rem 1rem;
              text-align: left;
            }
            th {
              background: #f8fafc;
              font-weight: 600;
              color: #0f172a;
            }
            blockquote {
              border-left: 4px solid #cbd5e1;
              padding-left: 1.25rem;
              color: #475569;
              margin: 1.5rem 0;
              font-style: italic;
            }
            @media print {
              body { padding: 0; }
            }
          </style>
        </head>
        <body>
          <div class="print-preview-container">
            ${document.getElementById('rendered-print-source').innerHTML}
          </div>
        </body>
      </html>
    `);
    doc.close();

    // Trigger printing
    setTimeout(() => {
      iframe.contentWindow.focus();
      iframe.contentWindow.print();
      document.body.removeChild(iframe);
    }, 500);
  };

  return (
    <div className="document-writer-container glass">
      {/* Control Panel: Prompts & Templates */}
      <div className="writer-control-panel">
        <h3 className="section-title">Document Generator</h3>
        <p className="panel-desc">Formulate premium workspace-grounded documents in real time.</p>
        
        <div className="template-picker-group">
          <label className="input-label">Select Document Template:</label>
          <div className="template-buttons">
            <button className="template-btn glass" onClick={() => handleSelectTemplate('software-spec')}>
              Tech Spec
            </button>
            <button className="template-btn glass" onClick={() => handleSelectTemplate('support-agreement')}>
              Support SLA
            </button>
            <button className="template-btn glass" onClick={() => handleSelectTemplate('project-plan')}>
              Project Roadmap
            </button>
            <button className="template-btn glass" onClick={() => handleSelectTemplate('data-privacy')}>
              Privacy Policy
            </button>
          </div>
        </div>

        <div className="prompt-input-group">
          <label className="input-label">Describe the document to write:</label>
          <textarea
            className="writer-prompt-textarea glass"
            placeholder="E.g. Create a structured software agreement containing detailed SLAs, based on templates in this workspace..."
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
          />
        </div>

        <button
          className={`generate-doc-btn ${loading ? 'loading' : ''}`}
          onClick={handleGenerate}
          disabled={loading || !prompt.trim()}
        >
          {loading ? (
            <>
              <span className="spinner"></span>
              Generating Draft...
            </>
          ) : (
            'Draft Grounded Document'
          )}
        </button>

        <AnimatePresence>
          {reasoning && (
            <motion.div
              className="grounding-audit-card glass"
              initial={{ opacity: 0, height: 0, y: 15 }}
              animate={{ opacity: 1, height: 'auto', y: 0 }}
              exit={{ opacity: 0, height: 0, y: 15 }}
              transition={{ duration: 0.3, ease: 'easeOut' }}
              style={{ overflow: 'hidden' }}
            >
              <div className="audit-header">
                <strong>Grounding Log</strong>
              </div>
              <p className="audit-content">{reasoning}</p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Editor & Preview Workspace */}
      <div className="writer-workspace-panel">
        <div className="workspace-header">
          {/* Mode Switcher */}
          <div className="mode-toggle-group glass" style={{ position: 'relative' }}>
            <button
              className={`mode-btn ${editorMode === 'split' ? 'active' : ''}`}
              onClick={() => setEditorMode('split')}
              style={{ position: 'relative' }}
            >
              {editorMode === 'split' && (
                <motion.div
                  layoutId="activeModePill"
                  className="active-mode-pill"
                  transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                />
              )}
              <span className="mode-btn-text">Split View</span>
            </button>
            <button
              className={`mode-btn ${editorMode === 'edit' ? 'active' : ''}`}
              onClick={() => setEditorMode('edit')}
              style={{ position: 'relative' }}
            >
              {editorMode === 'edit' && (
                <motion.div
                  layoutId="activeModePill"
                  className="active-mode-pill"
                  transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                />
              )}
              <span className="mode-btn-text">Markdown Source</span>
            </button>
            <button
              className={`mode-btn ${editorMode === 'preview' ? 'active' : ''}`}
              onClick={() => setEditorMode('preview')}
              style={{ position: 'relative' }}
            >
              {editorMode === 'preview' && (
                <motion.div
                  layoutId="activeModePill"
                  className="active-mode-pill"
                  transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                />
              )}
              <span className="mode-btn-text">Formatted Preview</span>
            </button>
          </div>

          {/* Export Toolbar */}
          <div className="toolbar-actions">
            <button className="tool-btn glass" onClick={handleCopy} disabled={!draft} title="Copy Markdown source">
              {copied ? 'Copied' : 'Copy MD'}
            </button>
            <button className="tool-btn glass" onClick={handleDownload} disabled={!draft} title="Download Markdown document">
              Download MD
            </button>
            <button className="tool-btn glass" onClick={handlePrint} disabled={!draft} title="Print / Export PDF">
              Export PDF
            </button>
          </div>
        </div>

        <div className={`workspace-body-layout mode-${editorMode}`}>
          {/* Markdown Source Area */}
          {(editorMode === 'split' || editorMode === 'edit') && (
            <div className="source-pane glass">
              <div className="pane-header">
                <span className="pane-title">Source Markdown</span>
              </div>
              <textarea
                className="markdown-textarea"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Compose or modify your markdown document draft here..."
                disabled={loading}
              />
            </div>
          )}

          {/* Formatted Render Area */}
          {(editorMode === 'split' || editorMode === 'preview') && (
            <div className="preview-pane glass" id="rendered-print-source">
              <div className="pane-header">
                <span className="pane-title">Grounded Preview</span>
              </div>
              {draft ? (
                <div className="markdown-body">
                  <ReactMarkdown>{draft}</ReactMarkdown>
                </div>
              ) : (
                <div className="preview-placeholder">
                  <div className="placeholder-icon-spin">✦</div>
                  <p className="placeholder-title">Awaiting Grounded Draft</p>
                  <p className="placeholder-sub">Select a template or describe your custom document, then click "Draft Grounded Document" to begin.</p>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DocumentWriter;
