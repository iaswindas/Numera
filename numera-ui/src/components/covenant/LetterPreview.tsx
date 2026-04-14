'use client'

import { useRef } from 'react'
import { Printer, Mail, Download } from 'lucide-react'
import { sanitizeHtml } from '@/utils/sanitize'

type LetterPreviewProps = {
  subject: string
  content: string
  onChange: (content: string) => void
  onPrint: () => void
  onSendEmail?: () => void
  onDownloadPdf?: () => void
}

export default function LetterPreview({ subject, content, onChange, onPrint, onSendEmail, onDownloadPdf }: LetterPreviewProps) {
  const previewRef = useRef<HTMLDivElement>(null)

  const handlePrint = () => {
    if (previewRef.current) {
      const printWindow = window.open('', '_blank')
      if (printWindow) {
        printWindow.document.write(`
          <!DOCTYPE html><html><head><title>${sanitizeHtml(subject || 'Waiver Letter')}</title>
          <style>body{font-family:Arial,sans-serif;padding:40px;line-height:1.6;color:#1a1a1a}</style>
          </head><body>${sanitizeHtml(content)}</body></html>
        `)
        printWindow.document.close()
        printWindow.print()
      }
    }
    onPrint()
  }

  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
      <div className="card-header" style={{ padding: '12px 16px' }}>
        <div>
          <div className="card-title">Letter Preview</div>
          <div className="card-subtitle">Subject: {subject || 'Untitled waiver letter'}</div>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <button type="button" className="btn btn-secondary btn-sm" onClick={handlePrint} title="Print">
            <Printer size={14} /> Print
          </button>
          {onSendEmail && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={onSendEmail} title="Send Email">
              <Mail size={14} /> Email
            </button>
          )}
          {onDownloadPdf && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={onDownloadPdf} title="Download PDF">
              <Download size={14} /> PDF
            </button>
          )}
        </div>
      </div>

      {/* Split view: left=editable HTML, right=rendered preview */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: 400 }}>
        {/* Left: editable source */}
        <div style={{ borderRight: '1px solid var(--border-color)', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '6px 12px', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)', textTransform: 'uppercase' }}>
            Edit HTML
          </div>
          <textarea
            className="input"
            value={content}
            onChange={(e) => onChange(e.target.value)}
            placeholder="Generated waiver letter content will appear here."
            style={{
              flex: 1,
              resize: 'none',
              border: 'none',
              borderRadius: 0,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              fontSize: 12,
              lineHeight: 1.55,
              padding: 12,
            }}
          />
        </div>

        {/* Right: rendered HTML preview */}
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '6px 12px', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)', textTransform: 'uppercase' }}>
            Rendered Preview
          </div>
          <div
            ref={previewRef}
            dangerouslySetInnerHTML={{ __html: sanitizeHtml(content) }}
            style={{
              flex: 1,
              padding: 24,
              overflow: 'auto',
              background: '#fff',
              color: '#1a1a1a',
              fontFamily: 'Arial, sans-serif',
              fontSize: 14,
              lineHeight: 1.6,
            }}
          />
        </div>
      </div>
    </div>
  )
}
