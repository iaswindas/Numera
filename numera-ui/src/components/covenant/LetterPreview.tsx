'use client'

type LetterPreviewProps = {
  subject: string
  content: string
  onChange: (content: string) => void
  onPrint: () => void
}

export default function LetterPreview({ subject, content, onChange, onPrint }: LetterPreviewProps) {
  return (
    <div className="card" style={{ padding: 16 }}>
      <div className="card-header">
        <div>
          <div className="card-title">Letter Preview</div>
          <div className="card-subtitle">Subject: {subject || 'Untitled waiver letter'}</div>
        </div>
        <button type="button" className="btn btn-secondary btn-sm" onClick={onPrint}>Print / Download</button>
      </div>

      <textarea
        className="input"
        rows={18}
        value={content}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Generated waiver letter content will appear here."
        style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace', lineHeight: 1.55 }}
      />
    </div>
  )
}
