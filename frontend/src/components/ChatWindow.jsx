import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import './ChatWindow.css';

const ChatWindow = () => {
  const [messages, setMessages] = useState([
    { role: 'ai', content: 'Welcome to the Knowledge Portal! I am your local AI assistant grounded securely in your documentation. Upload your files to the left and ask me any questions.' }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

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
      const response = await fetch('http://localhost:8080/api/chat/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: userMessage.content })
      });

      const data = await response.json();

      setMessages(prev => [...prev, {
        role: 'ai',
        content: data.answer,
        sources: data.sources,
        imageUrls: data.imageUrls,
        isRefusal: data.refusal
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
        {messages.map((msg, idx) => (
          <div key={idx} className={`message-wrapper ${msg.role}`}>
            <div className={`message-bubble ${msg.role} ${msg.isRefusal ? 'refusal' : ''}`}>
              {msg.role === 'ai' ? (
                <div className="markdown-body">
                  <ReactMarkdown>{msg.content}</ReactMarkdown>
                </div>
              ) : (
                <div className="message-content">{msg.content}</div>
              )}
              {msg.sources && msg.sources.length > 0 && (
                <div className="sources-container">
                  <strong>📎 Sources</strong>
                  <ul>
                    {msg.sources.map((src, i) => (
                      <li key={i}>
                        <span className="source-doc">📄 {src.document}</span>
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
                  <strong>🖼️ Related Screenshots</strong>
                  <div className="image-grid">
                    {msg.imageUrls.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="image-thumb-link">
                        <img
                          src={url}
                          alt={`Screenshot ${i + 1}`}
                          className="image-thumb"
                          onError={(e) => { e.target.closest('.image-thumb-link').style.display = 'none'; }}
                        />
                        <div className="image-zoom-hint">🔍 Click to enlarge</div>
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}
        {loading && (
          <div className="message-wrapper ai">
            <div className="message-bubble ai typing">
              <span className="dot"></span>
              <span className="dot"></span>
              <span className="dot"></span>
            </div>
          </div>
        )}
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
