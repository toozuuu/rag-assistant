import React from 'react';
import ChatWindow from './components/ChatWindow';
import FileUpload from './components/FileUpload';
import './index.css';

function App() {
  return (
    <div className="app-container">
      <header className="app-header">
        <h1>WMP Knowledge Portal</h1>
        <p>Secure enterprise document ingestion & contextual intelligence for WMP LLC.</p>
      </header>
      <div className="main-content">
        <FileUpload />
        <ChatWindow />
      </div>
    </div>
  );
}

export default App;
