'use client'

import { useState } from 'react'
import { Download, Share2, Copy, Check, X } from 'lucide-react'
import { useShareDashboard, type ShareDashboardResponse } from '@/services/portfolioApi'

export function DashboardExport() {
  const [showShareModal, setShowShareModal] = useState(false)
  const [expiryHours, setExpiryHours] = useState<number>(168) // 1 week
  const [title, setTitle] = useState('')
  const [shareResult, setShareResult] = useState<ShareDashboardResponse | null>(null)
  const [copied, setCopied] = useState(false)
  const [exporting, setExporting] = useState(false)

  const shareMutation = useShareDashboard()

  async function handleExportPdf() {
    setExporting(true)
    try {
      // Dynamic import to avoid SSR issues — uses html2canvas + jsPDF if available
      const dashboardEl = document.querySelector('.page-content') as HTMLElement | null
      if (!dashboardEl) return

      let html2canvas: typeof import('html2canvas')['default'] | null = null
      let jsPDF: typeof import('jspdf')['jsPDF'] | null = null
      try {
        const h2cMod = await import('html2canvas')
        html2canvas = h2cMod.default
        const jsMod = await import('jspdf')
        jsPDF = jsMod.jsPDF
      } catch {
        // Libraries not available — fall back to browser print
        window.print()
        return
      }

      const canvas = await html2canvas(dashboardEl, {
        scale: 2,
        useCORS: true,
        logging: false,
      })
      const imgData = canvas.toDataURL('image/png')
      const pdf = new jsPDF('landscape', 'mm', 'a4')
      const pdfWidth = pdf.internal.pageSize.getWidth()
      const pdfHeight = (canvas.height * pdfWidth) / canvas.width
      pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight)
      pdf.save('numera-dashboard.pdf')
    } finally {
      setExporting(false)
    }
  }

  function handleShare() {
    const config = JSON.stringify({
      page: window.location.pathname,
      timestamp: new Date().toISOString(),
    })
    shareMutation.mutate(
      { configJson: config, title: title || undefined, expiryHours },
      {
        onSuccess: (data) => setShareResult(data),
      },
    )
  }

  function handleCopyLink() {
    if (!shareResult) return
    const url = `${window.location.origin}${shareResult.shareUrl}`
    navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <>
      <div style={{ display: 'flex', gap: 8 }}>
        <button
          className="btn btn-secondary"
          onClick={handleExportPdf}
          disabled={exporting}
          style={{ display: 'flex', alignItems: 'center', gap: 6 }}
        >
          <Download size={14} />
          {exporting ? 'Exporting...' : 'Export PDF'}
        </button>
        <button
          className="btn btn-secondary"
          onClick={() => { setShowShareModal(true); setShareResult(null) }}
          style={{ display: 'flex', alignItems: 'center', gap: 6 }}
        >
          <Share2 size={14} />
          Share
        </button>
      </div>

      {showShareModal && (
        <div
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
          }}
          onClick={(e) => { if (e.target === e.currentTarget) setShowShareModal(false) }}
        >
          <div style={{
            background: 'var(--bg-card, #fff)', borderRadius: 12, padding: 24,
            width: 420, maxWidth: '90vw', boxShadow: '0 20px 60px rgba(0,0,0,0.15)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 16 }}>Share Dashboard</h3>
              <button className="btn btn-ghost" onClick={() => setShowShareModal(false)} style={{ padding: 4 }}>
                <X size={16} />
              </button>
            </div>

            {!shareResult ? (
              <>
                <div style={{ marginBottom: 12 }}>
                  <label style={{ fontSize: 13, fontWeight: 500 }}>Title (optional)</label>
                  <input
                    className="input"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Dashboard snapshot..."
                    style={{ marginTop: 4, width: '100%' }}
                  />
                </div>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ fontSize: 13, fontWeight: 500 }}>Link expiry</label>
                  <select
                    className="input"
                    value={expiryHours}
                    onChange={(e) => setExpiryHours(Number(e.target.value))}
                    style={{ marginTop: 4, width: '100%' }}
                  >
                    <option value={24}>24 hours</option>
                    <option value={168}>1 week</option>
                    <option value={720}>30 days</option>
                    <option value={2160}>90 days</option>
                  </select>
                </div>
                <button
                  className="btn btn-primary"
                  onClick={handleShare}
                  disabled={shareMutation.isPending}
                  style={{ width: '100%' }}
                >
                  {shareMutation.isPending ? 'Creating link...' : 'Create shareable link'}
                </button>
              </>
            ) : (
              <div>
                <div style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 8 }}>
                  Share link created. Expires {new Date(shareResult.expiresAt).toLocaleDateString()}.
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input
                    className="input"
                    readOnly
                    value={`${typeof window !== 'undefined' ? window.location.origin : ''}${shareResult.shareUrl}`}
                    style={{ flex: 1 }}
                  />
                  <button className="btn btn-primary" onClick={handleCopyLink} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    {copied ? <Check size={14} /> : <Copy size={14} />}
                    {copied ? 'Copied' : 'Copy'}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
