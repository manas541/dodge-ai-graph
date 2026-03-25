

import React, { useState } from 'react';
import GraphViewer from './components/GraphViewer';
import ChatPanel from './components/ChatPanel';

export default function App() {
  const [highlightNodes,  setHighlightNodes]  = useState([]);
  const [graphMinimized,  setGraphMinimized]  = useState(false);
  const [showOverlay,     setShowOverlay]     = useState(true);

  const handleHighlight = (ids) => {
  try {
    if (!ids || !Array.isArray(ids)) return;
    const safeIds = ids.filter(id => typeof id === 'string' && id.length > 0);
    if (safeIds.length === 0) return;
    setHighlightNodes(safeIds);
  } catch (e) {
    console.error("Highlight failed:", e);
  }
};

  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      height: '100vh', background: '#fff',
      fontFamily: "'Inter', -apple-system, sans-serif",
      overflow: 'hidden', color: '#111'
    }}>

      {/* Top breadcrumb bar */}
      <div style={{
        padding: '12px 20px',
        borderBottom: '1px solid #e5e7eb',
        display: 'flex', alignItems: 'center',
        gap: 8, fontSize: 14, color: '#6b7280',
        background: '#fff', zIndex: 10,
      }}>
        <span style={{ fontSize: 18 }}>⊞</span>
        <span>Mapping</span>
        <span style={{ color: '#d1d5db' }}>/</span>
        <span style={{ color: '#111', fontWeight: 600 }}>
          Order to Cash
        </span>
      </div>

      {/* Main content */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>

        {/* Graph area */}
        <div style={{
          flex: graphMinimized ? '0 0 0%' : '1',
          position: 'relative',
          transition: 'flex 0.3s ease',
          overflow: 'hidden',
        }}>
          {/* Floating buttons on graph */}
          <div style={{
            position: 'absolute', top: 16, left: 16,
            display: 'flex', gap: 8, zIndex: 20,
          }}>
            <button
              onClick={() => setGraphMinimized(!graphMinimized)}
              style={floatBtn}>
              {graphMinimized ? '⤢ Expand' : '⤡ Minimize'}
            </button>
            <button
              onClick={() => setShowOverlay(!showOverlay)}
              style={floatBtn}>
              ≡ {showOverlay ? 'Hide' : 'Show'} Granular Overlay
            </button>
          </div>

          <GraphViewer
            highlightNodes={highlightNodes}
            showOverlay={showOverlay}
          />
        </div>

        {/* Divider */}
        <div style={{ width: 1, background: '#e5e7eb', flexShrink: 0 }} />

        {/* Chat panel */}
        <div style={{ width: 360, flexShrink: 0 }}>
          <ChatPanel onHighlight={handleHighlight} />
        </div>

      </div>
    </div>
  );
}

const floatBtn = {
  padding: '6px 14px',
  background: '#111',
  color: '#fff',
  border: 'none',
  borderRadius: 6,
  fontSize: 12,
  cursor: 'pointer',
  fontFamily: "'Inter', sans-serif",
  display: 'flex',
  alignItems: 'center',
  gap: 6,
};