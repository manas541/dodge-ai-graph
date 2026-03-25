
import React, { useEffect, useRef, useState } from 'react';
import { Network } from 'vis-network';
import { DataSet } from 'vis-data';
import { fetchGraph, fetchNeighbor } from '../api/client';

export default function GraphViewer({ highlightNodes = [], showOverlay }) {
  const containerRef = useRef(null);
  const networkRef   = useRef(null);
  const nodesDS      = useRef(new DataSet());
  const edgesDS      = useRef(new DataSet());
  const [loading, setLoading] = useState(true);
  const [popup, setPopup] = useState(null);

const showPopup = (node, position) => {
  setPopup({ node, x: position.x, y: position.y });
};

  useEffect(() => { initGraph(); }, []);

  useEffect(() => {
  if (!networkRef.current || highlightNodes.length === 0) return;

  try {
    const existingIds = highlightNodes.filter(id => {
      try {
        return nodesDS.current.get(id) !== null;
      } catch {
        return false;
      }
    });

    if (existingIds.length === 0) return; 

    networkRef.current.selectNodes(existingIds);
    networkRef.current.fit({ nodes: existingIds, animation: true });
  } catch (e) {
    console.error('Highlight failed:', e);
  }
}, [highlightNodes]);

  
  const nodeStyle = (type) => {
    const styles = {
      Customer:       { color: '#1d6fa4', size: 14 },
      SalesOrder:     { color: '#3b82f6', size: 10 },
      SalesOrderItem: { color: '#93c5fd', size: 7  },
      Delivery:       { color: '#60a5fa', size: 9  },
      Invoice:        { color: '#e879a0', size: 9  },
      JournalEntry:   { color: '#f472b6', size: 7  },
      Payment:        { color: '#fb7185', size: 8  },
      Product:        { color: '#a78bfa', size: 7  },
    };
    return styles[type] || { color: '#93c5fd', size: 7 };
  };

  const formatNode = (node) => {
    const style = nodeStyle(node.type);
    return {
      id:         node.id,
      title:      buildTooltip(node),
      color: {
        background: style.color,
        border:     style.color,
        highlight:  { background: '#f59e0b', border: '#f59e0b' },
        hover:      { background: '#fbbf24', border: '#fbbf24' },
      },
      size:        style.size,
      shape:       'dot',
      label:       '',           
      nodeType:    node.type,
      properties:  node.properties,
      fullLabel:   node.label,
    };
  };

  const buildTooltip = (node) => {
  const props = Object.entries(node.properties || {})
    .filter(([k, v]) => v !== '' && v !== null && v !== undefined && v !== false)
    .slice(0, 8);

  const propsHTML = props
    .map(([k, v]) => `
      <div style="display:flex;justify-content:space-between;
        gap:16px;padding:3px 0;border-bottom:1px solid #f3f4f6">
        <span style="color:#9ca3af;font-size:11px;white-space:nowrap">${k}</span>
        <span style="color:#111;font-size:11px;font-weight:500;
          text-align:right;max-width:140px;overflow:hidden;
          text-overflow:ellipsis;white-space:nowrap">${v}</span>
      </div>
    `)
    .join('');

  const hiddenCount = Object.keys(node.properties || {}).length - props.length;

  return `
    <div style="
      padding:14px;
      font-family:'Inter',-apple-system,sans-serif;
      background:#fff;
      border:1px solid #e5e7eb;
      border-radius:10px;
      box-shadow:0 8px 24px rgba(0,0,0,0.12);
      min-width:240px;
      max-width:280px;
    ">
      <div style="font-weight:700;font-size:14px;color:#111;margin-bottom:2px">
        ${node.type}
      </div>
      <div style="font-size:11px;color:#6b7280;margin-bottom:10px">
        ${node.label}
      </div>
      <div style="border-top:1px solid #f3f4f6;padding-top:8px">
        ${propsHTML}
      </div>
      ${hiddenCount > 0 ? `
        <div style="margin-top:8px;font-size:11px;
          color:#9ca3af;font-style:italic">
          Additional fields hidden for readability
        </div>
      ` : ''}
      <div style="margin-top:8px;font-size:11px;color:#6b7280;
        display:flex;align-items:center;gap:4px">
        <span>Connections:</span>
        <span style="font-weight:600;color:#111">
          ${Object.keys(node.properties || {}).length}
        </span>
      </div>
    </div>
  `;
};

  const initGraph = async () => {
    try {
      setLoading(true);
      const { data } = await fetchGraph();

      const visNodes = data.nodes.map(formatNode);
      const visEdges = data.edges.map(e => ({
        id:     e.id,
        from:   e.source,
        to:     e.target,
        color:  { color: '#bfdbfe', opacity: 0.8,
                  highlight: '#3b82f6', hover: '#60a5fa' },
        width:  0.8,
        arrows: { to: { enabled: false } }, 
        smooth: { type: 'continuous' },
        title:  e.label,
      }));

      nodesDS.current.clear();
      edgesDS.current.clear();
      nodesDS.current.add(visNodes);
      edgesDS.current.add(visEdges);

      const network = new Network(
        containerRef.current,
        { nodes: nodesDS.current, edges: edgesDS.current },
        {
          physics: {
            enabled: true,
            solver: 'forceAtlas2Based',
            forceAtlas2Based: {
              gravitationalConstant: -40,
              centralGravity: 0.005,
              springLength: 100,
              springConstant: 0.05,
              damping: 0.9,
            },
            stabilization: { iterations: 200, updateInterval: 50 },
          },
          interaction: {
  hover: true,
  tooltipDelay: 100,       
  hideEdgesOnDrag: true,
  navigationButtons: false,
  zoomView: true,
  dragView: true,
},
          nodes: {
            borderWidth: 0,
            shadow: false,
          },
          edges: {
            shadow: false,
          },
        }
      );

      networkRef.current = network;
      
network.on('click', ({ nodes, pointer }) => {
  if (nodes.length > 0) {
    const node = nodesDS.current.get(nodes[0]);
    if (node) showPopup(node, pointer.DOM);
  } else {
    setPopup(null);
  }
});

      network.on('doubleClick', async ({ nodes }) => {
        if (nodes.length > 0) await expandNode(nodes[0]);
      });

    } catch (err) {
      console.error('Graph error:', err);
    } finally {
      setLoading(false);
    }
  };

  const expandNode = async (nodeId) => {
    try {
      const { data } = await fetchNeighbor(nodeId);
      const newNodes = (data.nodes || [])
        .filter(n => n && !nodesDS.current.get(n.id))
        .map(formatNode);
      const newEdges = (data.edges || [])
        .filter(e => e && !edgesDS.current.get(e.id))
        .map(e => ({
          id: e.id, from: e.source, to: e.target,
          color: { color: '#bfdbfe', opacity: 0.8 },
          width: 0.8,
          arrows: { to: { enabled: false } },
          smooth: { type: 'continuous' },
        }));
      if (newNodes.length) nodesDS.current.add(newNodes);
      if (newEdges.length) edgesDS.current.add(newEdges);
    } catch (err) {
      console.error('Expand error:', err);
    }
  };

  return (
    <div style={{ width: '100%', height: '100%', position: 'relative', background: '#f8fafc' }}>
      {loading && (
        <div style={{
          position: 'absolute', inset: 0,
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
          background: '#f8fafc', zIndex: 5, gap: 12,
        }}>
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            border: '3px solid #bfdbfe',
            borderTopColor: '#3b82f6',
            animation: 'spin 0.8s linear infinite',
          }} />
          <span style={{ color: '#6b7280', fontSize: 13 }}>
            Loading graph...
          </span>
          <style>{`@keyframes spin { to { transform: rotate(360deg); }}`}</style>
        </div>
      )}

      {/* Legend overlay */}
      {showOverlay && !loading && (
        <div style={{
          position: 'absolute', bottom: 16, left: 16,
          background: '#fff', border: '1px solid #e5e7eb',
          borderRadius: 8, padding: '10px 14px',
          fontSize: 11, zIndex: 10,
          boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
        }}>
          {[
  { type: 'Customer',       color: '#1d6fa4' },
  { type: 'SalesOrder',     color: '#3b82f6' },
  { type: 'SalesOrderItem', color: '#93c5fd' },
  { type: 'Delivery',       color: '#60a5fa' },
  { type: 'Invoice',        color: '#e879a0' },
  { type: 'JournalEntry',   color: '#f472b6' },
  { type: 'Payment',        color: '#fb7185' },
  { type: 'Product',        color: '#a78bfa' },
].map(({ type, color }) => (
            <div key={type} style={{
              display: 'flex', alignItems: 'center',
              gap: 6, marginBottom: 4, color: '#374151',
            }}>
              <div style={{
                width: 8, height: 8, borderRadius: '50%',
                background: color, flexShrink: 0,
              }} />
              {type}
            </div>
          ))}
        </div>
      )}

      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      {/* Click popup card */}
{popup && (
  <div style={{
    position: 'absolute',
    left: Math.min(popup.x + 10, window.innerWidth - 320),
    top: Math.min(popup.y + 10, window.innerHeight - 400),
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 10,
    padding: 16,
    boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
    zIndex: 100,
    minWidth: 240,
    maxWidth: 300,
    fontFamily: "'Inter', sans-serif",
  }}>
    {/* Header */}
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      alignItems: 'flex-start', marginBottom: 10
    }}>
      <div>
        <div style={{ fontWeight: 700, fontSize: 14, color: '#111' }}>
          {popup.node.nodeType}
        </div>
        <div style={{ fontSize: 11, color: '#6b7280', marginTop: 2 }}>
          {popup.node.fullLabel}
        </div>
      </div>
      <button onClick={() => setPopup(null)} style={{
        background: 'none', border: 'none',
        cursor: 'pointer', color: '#9ca3af',
        fontSize: 16, padding: 0, lineHeight: 1,
      }}>✕</button>
    </div>

    {/* Properties */}
    <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: 8 }}>
      {Object.entries(popup.node.properties || {})
        .filter(([k, v]) => v !== '' && v !== null && v !== false)
        .slice(0, 8)
        .map(([k, v]) => (
          <div key={k} style={{
            display: 'flex', justifyContent: 'space-between',
            gap: 12, padding: '4px 0',
            borderBottom: '1px solid #f9fafb',
          }}>
            <span style={{ color: '#9ca3af', fontSize: 11 }}>{k}</span>
            <span style={{
              color: '#111', fontSize: 11, fontWeight: 500,
              textAlign: 'right', maxWidth: 160,
              overflow: 'hidden', textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}>{String(v)}</span>
          </div>
        ))
      }
    </div>

    {/* Connections count */}
    <div style={{
      marginTop: 8, fontSize: 11,
      color: '#6b7280', display: 'flex',
      alignItems: 'center', gap: 4,
    }}>
      <span>Connections:</span>
      <span style={{ fontWeight: 600, color: '#111' }}>
        {Object.keys(popup.node.properties || {}).length}
      </span>
    </div>

    <div style={{
      marginTop: 8, fontSize: 11,
      color: '#9ca3af', fontStyle: 'italic'
    }}>
      Double-click to expand connections
    </div>
  </div>
)}
    </div>
  );
}