import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { motion, AnimatePresence } from 'motion/react';
import { getApiUrl, getActiveLlmConfig } from '../api';
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

// Copyable Code Block component
const CodeBlock = ({ inline, className, children, ...props }) => {
  const [copied, setCopied] = useState(false);
  const match = /language-(\w+)/.exec(className || '');
  const lang = match ? match[1] : '';
  const codeString = String(children).replace(/\n$/, '');

  if (inline) {
    return <code className="inline-code" {...props}>{children}</code>;
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(codeString);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="code-block-container">
      <div className="code-block-header">
        <span className="code-lang-label">{lang || 'code'}</span>
        <button className="copy-code-btn" onClick={handleCopy}>
          {copied ? '✓ Copied' : '📋 Copy'}
        </button>
      </div>
      <pre className={`code-pre ${className || ''}`}>
        <code {...props}>{children}</code>
      </pre>
    </div>
  );
};

// Interactive overlay component for inline citations
const CitationMarker = ({ index, sources }) => {
  const [hovered, setHovered] = useState(false);
  const source = sources && sources[index];

  if (!source) {
    return <span className="citation-marker-fallback">[{index + 1}]</span>;
  }

  const isCode = Boolean(source.filePath);
  const displayName = source.filePath || source.document;

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
            <span className="tooltip-filename">
              {isCode ? `💻 ${displayName}` : `📄 ${displayName}`}
            </span>
            {source.language && (
              <span className="tooltip-badge">.{source.language}</span>
            )}
            {source.pageNumber && (
              <span className="tooltip-page">Page {source.pageNumber}</span>
            )}
          </span>
          {source.snippet && (
            <span className="tooltip-snippet">
              "{source.snippet.length > 180 ? source.snippet.substring(0, 180) + '...' : source.snippet}"
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

const QA_PROMPTS = [
  { label: '🧪 Generate BDD Test Cases', prompt: 'Generate comprehensive Gherkin/BDD test scenarios (Given-When-Then) covering positive, negative, and edge cases based on the requirements and codebase logic.' },
  { label: '📋 Extract Validation Rules', prompt: 'List all input validation constraints, business rules, and error handling conditions implemented in this feature/codebase.' },
  { label: '🔌 API Contracts & Payloads', prompt: 'List all REST API endpoints, HTTP methods, expected request/response payloads, and HTTP status codes.' },
  { label: '🛡️ Security & Boundary Audit', prompt: 'Audit boundary conditions, authentication checks, SQL/input sanitization, and unhandled exception scenarios for QA testing.' },
  { label: '🧩 Unit / Mock Test Suite', prompt: 'Generate unit test cases with Mockito/JUnit 5 (or Jest/PyTest) covering branch conditions and error scenarios.' },
];

const ChatWindow = ({ token, workspace, onAuthError }) => {
  const [prevWorkspace, setPrevWorkspace] = useState(workspace);
  const [messages, setMessages] = useState(() => [
    {
      role: 'ai',
      content: `Welcome to the **Knowledge & Code QA Assistant**! I am grounded in your **${workspace || 'default'}** workspace repository.\n\nAsk me any questions regarding requirements, code business logic, test scenarios, or API contracts.`
    }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId] = useState(null);
  const messagesEndRef = useRef(null);

  if (workspace !== prevWorkspace) {
    setPrevWorkspace(workspace);
    setMessages([
      {
        role: 'ai',
        content: `Welcome to the **Knowledge & Code QA Assistant**! I am grounded in your **${workspace || 'default'}** workspace repository.\n\nAsk me any questions regarding requirements, code business logic, test scenarios, or API contracts.`
      }
    ]);
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const saveConversation = async (msgs) => {
    try {
      const history = msgs
        .filter(m => m.role !== 'ai' || (!m.isRefusal && m.content))
        .slice(-20)
        .map(m => ({ role: m.role === 'ai' ? 'assistant' : 'user', content: m.content }));
      await fetch(getApiUrl('/api/conversations'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ conversationId, messages: history })
      });
    } catch {
      // Ignore conversation save errors
    }
  };

  const handleSend = async (customPrompt) => {
    const textToSend = typeof customPrompt === 'string' ? customPrompt : input;
    if (!textToSend.trim()) return;

    const userMessage = { role: 'user', content: textToSend.trim() };
    const updatedMessages = [...messages, userMessage];
    setMessages(updatedMessages);
    setInput('');
    setLoading(true);

    try {
      let currentToken = token;
      const history = messages
        .filter(m => m.role !== 'ai' || (!m.isRefusal && m.content))
        .slice(-10)
        .map(m => ({ role: m.role === 'ai' ? 'assistant' : 'user', content: m.content }));
      const body = JSON.stringify({
        question: userMessage.content,
        workspace: workspace || 'default',
        history,
        llmConfig: getActiveLlmConfig()
      });

      let response = await fetch(getApiUrl('/api/chat/ask'), {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${currentToken}`
        },
        body
      });

      if (response.status === 401 || response.status === 403) {
        if (onAuthError) {
          const newToken = await onAuthError();
          if (newToken) {
            currentToken = newToken;
            response = await fetch(getApiUrl('/api/chat/ask'), {
              method: 'POST',
              headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
              },
              body
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
      const aiResponse = {
        role: 'ai',
        content: data.answer,
        sources: data.sources,
        imageUrls: data.imageUrls,
        isRefusal: data.refusal,
        reasoning: data.reasoning,
        confidenceScore: data.confidenceScore
      };

      const finalMessages = [...updatedMessages, aiResponse];
      setMessages(finalMessages);
      saveConversation(finalMessages);
    } catch {
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
      {/* QA Quick Actions Header */}
      <div className="qa-quick-actions">
        <span className="qa-actions-title">⚡ QA Workflows:</span>
        <div className="qa-chips-scroll">
          {QA_PROMPTS.map((qa, i) => (
            <button
              key={i}
              className="qa-chip-btn glass"
              onClick={() => handleSend(qa.prompt)}
              disabled={loading}
              title={qa.prompt}
            >
              {qa.label}
            </button>
          ))}
        </div>
      </div>

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
                          code: CodeBlock,
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
                    <strong>Grounding Sources & Code Files</strong>
                    <ul>
                      {msg.sources.map((src, i) => (
                        <li key={i}>
                          <span className="source-doc">
                            {src.filePath ? `💻 ${src.filePath}` : `📄 ${src.document}`}
                          </span>
                          {src.language && (
                            <span className="source-section"> [{src.language}]</span>
                          )}
                          {src.pageNumber && (
                            <span className="source-section"> (Page {src.pageNumber})</span>
                          )}
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
          placeholder="Ask a question about requirements, code logic, or test scenarios..."
        />
        <button onClick={() => handleSend()} disabled={loading || !input.trim()}>
          Send
        </button>
      </div>
    </div>
  );
};

export default ChatWindow;