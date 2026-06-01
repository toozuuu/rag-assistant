import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { motion, AnimatePresence } from 'motion/react';
import './ChatWindow.css';

// Preprocessor to replace [cit:X] syntax with markdown links [[cit-X]](#cit-X)
const processCitations = (text) => {
  if (!text) return '';
  return text.replace(/\[cit:(\d+)\]/g, (match, p1) => {
    const idx = parseInt(p1, 10);
    const label = `[${idx + 1}]`;
    return `[${label}](#cit-${idx})`;
  });
};

// Interactive overlay component for inline citations
const CitationMarker = ({ index, sources }) => {
  const [hovered, setHovered] = useState(false);
  const source = sources && sources[index];

  if (!source) {
    return <span className="citation-marker-fallback">[{index + 1}]</span>;
  }

  return (
    <span 
      className="citation-container"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <span className="citation-marker">[{index + 1}]</span>
      {hovered && (
        <span className="citation-tooltip glass animate-fade-in">
          <span className="tooltip-header">
            <span className="tooltip-filename">{source.document}</span>
            {source.pageNumber && (
              <span className="tooltip-page">Page {source.pageNumber}</span>
            )}
          </span>
          {source.snippet && (
            <span className="tooltip-snippet">
              "{source.snippet.length > 155 ? source.snippet.substring(0, 155) + '...' : source.snippet}"
            </span>
          )}
        </span>
      )}
    </span>
  );
};

// Encapsulated ThoughtProcess Component with Framer Motion slide-down animations
const ThoughtProcess = ({ reasoning, confidenceScore }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className={`thought-process-container glass ${isOpen ? 'open' : ''}`}>
      <button 
        className="thought-process-summary"
        onClick={() => setIsOpen(!isOpen)}
      >
        <span>Grounding Thought Process (Confidence: {(confidenceScore * 100).toFixed(0)}%)</span>
        <span className="expand-indicator">▼</span>
      </button>
      <AnimatePresence initial={false}>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: 'easeInOut' }}
            style={{ overflow: 'hidden' }}
          >
            <div className="thought-process-content">
              {reasoning}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

const ChatWindow = ({ token, workspace, onAuthError }) => {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  // ── Reset conversation thread whenever workspace switches ───────────
  useEffect(() => {
    setMessages([
      {
        role: 'ai',
        content: `Welcome to the Knowledge Portal! I am grounded in your **${workspace || 'default'}** workspace document repository. Ingest files to the left and ask me any questions.`
      }
    ]);
  }, [workspace]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage = { role: 'user', content: input };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    try {
      const apiBase = import.meta.env.VITE_API_BASE_URL || (window.location.hostname === 'localhost' ? 'http://localhost:8080' : '');
      let currentToken = token;
      let response = await fetch(`${apiBase}/api/chat/ask`, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${currentToken}`
        },
        body: JSON.stringify({ question: userMessage.content, workspace: workspace || 'default' })
      });

      if (response.status === 401 || response.status === 403) {
        if (onAuthError) {
          const newToken = await onAuthError();
          if (newToken) {
            currentToken = newToken;
            response = await fetch(`${apiBase}/api/chat/ask`, {
              method: 'POST',
              headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
              },
              body: JSON.stringify({ question: userMessage.content, workspace: workspace || 'default' })
            });
          }
        }
      }

      if (response.status === 401 || response.status === 403) {
        setMessages(prev => [...prev, {
          role: 'ai',
          content: 'Authentication failed. Please verify your connection or try again.'
        }]);
        return;
      }

      const data = await response.json();

      setMessages(prev => [...prev, {
        role: 'ai',
        content: data.answer,
        sources: data.sources,
        imageUrls: data.imageUrls,
        isRefusal: data.refusal,
        reasoning: data.reasoning,
        confidenceScore: data.confidenceScore
      }]);
    } catch (error) {
      setMessages(prev => [...prev, {
        role: 'ai',
        content: 'Sorry, I encountered an error connecting to the server.'
      }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="chat-window glass">
      <div className="messages-container">
        <AnimatePresence initial={false}>
          {messages.map((msg, idx) => (
            <motion.div 
              key={idx} 
              className={`message-wrapper ${msg.role}`}
              initial={{ opacity: 0, y: 15, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
            >
              <div className={`message-bubble ${msg.role} ${msg.isRefusal ? 'refusal' : ''}`}>
                {msg.role === 'ai' ? (
                  <>
                    {msg.reasoning && (
                      <ThoughtProcess reasoning={msg.reasoning} confidenceScore={msg.confidenceScore} />
                    )}
                    <div className="markdown-body">
                      <ReactMarkdown
                        components={{
                          a: ({ href, children }) => {
                            if (href && href.startsWith('#cit-')) {
                              const idx = parseInt(href.replace('#cit-', ''), 10);
                              return <CitationMarker index={idx} sources={msg.sources} />;
                            }
                            return <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>;
                          }
                        }}
                      >
                        {processCitations(msg.content)}
                      </ReactMarkdown>
                    </div>
                  </>
                ) : (
                  <div className="message-content">{msg.content}</div>
                )}
                {msg.sources && msg.sources.length > 0 && (
                  <div className="sources-container">
                    <strong>Sources</strong>
                    <ul>
                      {msg.sources.map((src, i) => (
                        <li key={i}>
                          <span className="source-doc">{src.document}</span>
                          {src.section && src.section !== 'Snippet' && (
                            <span className="source-section"> — {src.section}</span>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {msg.imageUrls && msg.imageUrls.length > 0 && (
                  <div className="image-gallery">
                    <strong>Related Screenshots</strong>
                    <div className="image-grid">
                      {msg.imageUrls.map((url, i) => (
                        <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="image-thumb-link">
                          <img
                            src={url}
                            alt={`Screenshot ${i + 1}`}
                            className="image-thumb"
                            onError={(e) => { e.target.closest('.image-thumb-link').style.display = 'none'; }}
                          />
                          <div className="image-zoom-hint">Click to enlarge</div>
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </motion.div>
          ))}
          {loading && (
            <motion.div 
              key="typing-loader"
              className="message-wrapper ai"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              <div className="message-bubble ai typing">
                <span className="dot"></span>
                <span className="dot"></span>
                <span className="dot"></span>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
        <div ref={messagesEndRef} />
      </div>

      <div className="input-area">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSend()}
          placeholder="Ask a question about your documents..."
        />
        <button onClick={handleSend} disabled={loading || !input.trim()}>
          Send
        </button>
      </div>
    </div>
  );
};

export default ChatWindow;
