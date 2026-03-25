
import React, { useState, useRef, useEffect } from 'react';
import { sendMessage, clearChat } from '../api/client';

const EXAMPLES = [
  "Which products have the most billing documents?",
  "Trace full flow of sales order 740506",
  "Show orders delivered but never invoiced",
  "Which customers have unpaid invoices?",
  "List all cancelled billing documents",
  "How many sales orders are there?",
];

export default function ChatPanel({ onHighlight }) {
  const [messages, setMessages] = useState([]);
  const [input,    setInput]    = useState('');
  const [loading,  setLoading]  = useState(false);
  const bottomRef  = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const send = async (text) => {
    const msg = (text || input).trim();
    if (!msg || loading) return;
    setInput('');
    setMessages(prev => [...prev, { role: 'user', content: msg }]);
    setLoading(true);

    try {
      const { data } = await sendMessage(msg);
      setMessages(prev => [...prev, {
        role:    'assistant',
        content: data.answer,
        sql:     data.sql,
        data:    data.data,
        blocked: data.blocked,
      }]);
      if (data.data && onHighlight) {
  try {
    const ids = extractIds(data.data);
    if (ids.length > 0) onHighlight(ids);
  } catch (e) {
    console.error("Highlight error:", e);
  }
}
    } catch {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: 'Cannot reach backend. Make sure Spring Boot is running on port 8080.',
      }]);
    } finally {
      setLoading(false);
    }
  };

  const extractIds = (rows) => {
  const ids = [];
  rows?.forEach(row => {
    Object.entries(row).forEach(([key, val]) => {
      if (typeof val !== 'string') return;

      // Sales orders — exactly 6 digits
      if (/^\d{6}$/.test(val))
        ids.push('SO-' + val);

      // Delivery docs — starts with 807, exactly 10 digits
      if (/^807\d{7}$/.test(val))
        ids.push('DEL-' + val);

      // Billing docs — starts with 905, exactly 8 digits
      // AND only from billing-related columns
      if (/^905\d{5}$/.test(val) &&
         (key === 'billing_document' || key === 'reference_document'))
        ids.push('BILL-' + val);

      // Accounting/payment docs — starts with 940, exactly 10 digits
      // AND only from payment-related columns
      if (/^940\d{7}$/.test(val) &&
         (key === 'accounting_document' || key === 'payment_document'))
        ids.push('PAY-' + val);

      // Business partners — starts with 31 or 32, exactly 9 digits
      if (/^3[12]\d{7}$/.test(val))
        ids.push('BP-' + val);
    });
  });
  return [...new Set(ids)].filter(Boolean);
};

  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      height: '100%', background: '#fff',
      fontFamily: "'Inter', -apple-system, sans-serif",
    }}>

      {/* Header */}
      <div style={{
        padding: '16px 20px',
        borderBottom: '1px solid #f3f4f6',
      }}>
        <div style={{ fontSize: 12, color: '#9ca3af', marginBottom: 12 }}>
          Chat with Graph
        </div>
        <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>
          Order to Cash
        </div>

        {/* Agent identity */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            background: '#111',
            display: 'flex', alignItems: 'center',
            justifyContent: 'center', color: '#fff',
            fontWeight: 700, fontSize: 14, flexShrink: 0,
          }}>D</div>
          <div>
            <div style={{ fontWeight: 600, fontSize: 14, color: '#111' }}>
              Dodge AI
            </div>
            <div style={{ fontSize: 12, color: '#9ca3af' }}>
              Graph Agent
            </div>
          </div>
        </div>
      </div>

      {/* Messages area */}
      <div style={{
        flex: 1, overflowY: 'auto',
        padding: '16px 20px',
        display: 'flex', flexDirection: 'column', gap: 16,
      }}>

        {/* Welcome message */}
        {messages.length === 0 && (
          <div style={{ fontSize: 14, color: '#374151', lineHeight: 1.6 }}>
            Hi! I can help you analyze the{' '}
            <strong>Order to Cash</strong> process.
          </div>
        )}

        {messages.map((msg, i) => (
          <Message key={i} msg={msg} />
        ))}

        {loading && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 28, height: 28, borderRadius: '50%',
              background: '#111',
              display: 'flex', alignItems: 'center',
              justifyContent: 'center', fontSize: 11,
              color: '#fff', fontWeight: 700, flexShrink: 0,
            }}>D</div>
            <div style={{
              display: 'flex', gap: 4, alignItems: 'center',
              padding: '8px 12px', background: '#f9fafb',
              borderRadius: 8, fontSize: 13, color: '#6b7280',
            }}>
              <span>Thinking</span>
              <span style={{ animation: 'blink 1s infinite' }}>...</span>
              <style>{`@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0.3} }`}</style>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Example queries */}
      <div style={{
        padding: '10px 20px',
        borderTop: '1px solid #f3f4f6',
      }}>
        <div style={{ fontSize: 11, color: '#9ca3af', marginBottom: 6 }}>
          SUGGESTIONS
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {EXAMPLES.map((q, i) => (
            <button key={i} onClick={() => send(q)} style={{
              fontSize: 11, padding: '4px 10px',
              background: '#f3f4f6', color: '#374151',
              border: 'none', borderRadius: 20,
              cursor: 'pointer', whiteSpace: 'nowrap',
            }}>
              {q.length > 35 ? q.slice(0, 35) + '...' : q}
            </button>
          ))}
        </div>
      </div>

      {/* Input area */}
      <div style={{
        padding: '12px 20px',
        borderTop: '1px solid #f3f4f6',
      }}>
        {/* Status indicator */}
        <div style={{
          display: 'flex', alignItems: 'center',
          gap: 6, marginBottom: 8,
          fontSize: 12, color: '#6b7280',
        }}>
          <div style={{
            width: 7, height: 7, borderRadius: '50%',
            background: loading ? '#f59e0b' : '#22c55e',
          }} />
          {loading ? 'Dodge AI is thinking...' : 'Dodge AI is awaiting instructions'}
        </div>

        {/* Input box */}
        <div style={{
          display: 'flex', gap: 8, alignItems: 'flex-end',
          border: '1px solid #e5e7eb', borderRadius: 10,
          padding: '8px 12px', background: '#fff',
        }}>
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && !e.shiftKey && send()}
            placeholder="Analyze anything"
            disabled={loading}
            style={{
              flex: 1, border: 'none', outline: 'none',
              fontSize: 14, color: '#111',
              background: 'transparent',
              fontFamily: "'Inter', sans-serif",
              resize: 'none',
            }}
          />
          <button
            onClick={() => send()}
            disabled={loading || !input.trim()}
            style={{
              padding: '6px 14px',
              background: input.trim() ? '#111' : '#e5e7eb',
              color: input.trim() ? '#fff' : '#9ca3af',
              border: 'none', borderRadius: 6,
              fontSize: 13, cursor: input.trim() ? 'pointer' : 'default',
              fontFamily: "'Inter', sans-serif",
              transition: 'all 0.2s',
            }}>
            Send
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Message component ────────────────────────────
function Message({ msg }) {
  const [showSQL,  setShowSQL]  = useState(false);
  const [showData, setShowData] = useState(false);
  const isUser = msg.role === 'user';

  if (!msg || !msg.content) return null;

  try {
    if (isUser) {
      return (
        <div style={{
          alignSelf: 'flex-end',
          background: '#111', color: '#fff',
          padding: '8px 14px',
          borderRadius: '12px 12px 2px 12px',
          fontSize: 14, maxWidth: '80%', lineHeight: 1.5,
        }}>
          {msg.content}
        </div>
      );
    }

    return (
      <div style={{
        display: 'flex', gap: 10, alignItems: 'flex-start'
      }}>
        <div style={{
          width: 28, height: 28, borderRadius: '50%',
          background: '#111', color: '#fff',
          display: 'flex', alignItems: 'center',
          justifyContent: 'center', fontSize: 11,
          fontWeight: 700, flexShrink: 0,
        }}>D</div>

        <div style={{ flex: 1 }}>
          <div style={{
            fontSize: 14, color: '#374151', lineHeight: 1.6,
            background: msg.blocked ? '#fef2f2' : 'transparent',
            padding: msg.blocked ? '8px 12px' : 0,
            borderRadius: msg.blocked ? 8 : 0,
            border: msg.blocked ? '1px solid #fecaca' : 'none',
          }}>
            {msg.content}
          </div>

          {msg.data && msg.data.length > 0 && (
            <div style={{ marginTop: 6 }}>
              <button onClick={() => setShowData(!showData)} style={{
                fontSize: 11, color: '#9ca3af',
                background: 'none', border: 'none',
                cursor: 'pointer', padding: 0,
                display: 'flex', alignItems: 'center', gap: 4,
              }}>
                <span>{showData ? '▼' : '▶'}</span>
                <span>{msg.data.length} rows returned</span>
              </button>
              {showData && (
                <div style={{ marginTop: 6 }}>
                  <DataTable data={msg.data.slice(0, 100)} />
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    );
  } catch (err) {
    console.error('Message render error:', err);
    return (
      <div style={{
        padding: '8px 12px', background: '#fef2f2',
        border: '1px solid #fecaca', borderRadius: 6,
        fontSize: 12, color: '#dc2626',
      }}>
        ⚠️ Error displaying message — check console.
      </div>
    );
  }
}

// ── Data Table ───────────────────────────────────
function DataTable({ data }) {
  if (!data?.length) return null;

  if (data[0]?.error) {
    return (
      <div style={{
        padding: '8px 12px', background: '#fef2f2',
        border: '1px solid #fecaca', borderRadius: 6,
        fontSize: 12, color: '#dc2626',
      }}>
        ⚠️ {String(data[0].error).slice(0, 200)}
      </div>
    );
  }

  const cols = Object.keys(data[0]);

  const safeVal = (val) => {
    try {
      if (val === null || val === undefined) return '—';
      if (typeof val === 'boolean') return val ? 'Yes' : 'No';
      if (typeof val === 'object') return JSON.stringify(val).slice(0, 80);
      const str = String(val);
      return str.length > 150 ? str.slice(0, 150) + '...' : str;
    } catch { return '—'; }
  };

  return (
    <div style={{
      overflowX: 'auto', maxHeight: 300,
      overflowY: 'auto', border: '1px solid #e5e7eb',
      borderRadius: 8,
    }}>
      <table style={{
        borderCollapse: 'collapse', fontSize: 12,
        width: '100%', minWidth: 'max-content',
      }}>
        <thead style={{ position: 'sticky', top: 0, zIndex: 1 }}>
          <tr style={{ background: '#f9fafb' }}>
            {cols.map(c => (
              <th key={c} style={{
                padding: '6px 10px', textAlign: 'left',
                color: '#6b7280', fontWeight: 500,
                fontSize: 11, whiteSpace: 'nowrap',
                borderBottom: '1px solid #e5e7eb',
              }}>{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={i} style={{
              borderTop: '1px solid #f3f4f6',
              background: i % 2 === 0 ? '#fff' : '#fafafa',
            }}>
              {cols.map(c => (
                <td key={c} style={{
                  padding: '6px 10px', color: '#374151',
                  whiteSpace: 'nowrap', fontSize: 12,
                  maxWidth: 200, overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}>
                  {safeVal(row[c])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}