import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

// music-metadata-browser 依赖 Node 全局 Buffer，此处 polyfill
import { Buffer } from 'buffer';
if (typeof (globalThis as Record<string, unknown>).Buffer === 'undefined') {
  (globalThis as Record<string, unknown>).Buffer = Buffer;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
