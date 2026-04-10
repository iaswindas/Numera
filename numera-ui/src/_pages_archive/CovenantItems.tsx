import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock, XCircle, Shield, FileText, Send, Eye } from 'lucide-react'

const financialItems = [
  {id:1,customer:'Emirates NBD',covenant:'Debt / EBITDA ≤ 4.0x',period:'Q4 2025',due:'2026-03-31',calculated:'3.82x',threshold:'≤ 4.0x',status:'met',probability:22},
  {id:2,customer:'Emirates NBD',covenant:'Interest Coverage ≥ 2.5x',period:'Q4 2025',due:'2026-03-31',calculated:'4.12x',threshold:'≥ 2.5x',status:'met',probability:8},
  {id:3,customer:'HSBC Holdings',covenant:'Tier 1 Capital ≥ 12%',period:'FY 2025',due:'2026-04-30',calculated:'—',threshold:'≥ 12%',status:'due',probability:45},
  {id:4,customer:'DP World',covenant:'DSCR ≥ 1.5x',period:'Q4 2025',due:'2026-03-15',calculated:'1.38x',threshold:'≥ 1.5x',status:'breached',probability:92},
  {id:5,customer:'DP World',covenant:'Net Debt / EBITDA ≤ 3.5x',period:'Q4 2025',due:'2026-03-15',calculated:'3.72x',threshold:'≤ 3.5x',status:'breached',probability:88},
  {id:6,customer:'ADNOC Dist.',covenant:'Current Ratio ≥ 1.2x',period:'Q3 2025',due:'2025-12-31',calculated:'—',threshold:'≥ 1.2x',status:'overdue',probability:65},
  {id:7,customer:'Saudi Aramco',covenant:'Debt / Equity ≤ 0.5x',period:'FY 2025',due:'2026-04-30',calculated:'0.31x',threshold:'≤ 0.5x',status:'met',probability:5},
  {id:8,customer:'Emirates NBD',covenant:'NPL Ratio ≤ 5%',period:'Q3 2025',due:'2025-12-31',calculated:'3.8%',threshold:'≤ 5%',status:'closed',probability:0},
]

const nonFinancialItems = [
  {id:1,customer:'Emirates NBD',item:'Audited Financial Statements',period:'FY 2025',due:'2026-04-30',status:'submitted',type:'Financial Statement'},
  {id:2,customer:'HSBC Holdings',item:'Board Resolution',period:'FY 2025',due:'2026-03-31',status:'approved',type:'Document'},
  {id:3,customer:'DP World',item:'Insurance Certificate',period:'FY 2025',due:'2026-03-15',status:'overdue',type:'Document'},
  {id:4,customer:'ADNOC Dist.',item:'Audited Financial Statements',period:'FY 2025',due:'2026-04-30',status:'due',type:'Financial Statement'},
  {id:5,customer:'Saudi Aramco',item:'Environmental Compliance Report',period:'FY 2025',due:'2026-04-30',status:'rejected',type:'Other'},
]

export default function CovenantItems() {
  const [mainTab, setMainTab] = useState<'financial'|'nonfinancial'>('financial')
  const [subTab, setSubTab] = useState<'all'|'pending'|'violated'>('all')
  const [showWaiver, setShowWaiver] = useState(false)

  const filtered = subTab === 'pending' ? financialItems.filter(i=>['due','overdue'].includes(i.status)) :
                   subTab === 'violated' ? financialItems.filter(i=>i.status==='breached') : financialItems

  return (
    <>
      <div className="page-header">
        <h1>Covenant Monitoring Items</h1>
        <p>Track and manage financial and non-financial covenant obligations</p>
      </div>

      <div className="stat-grid" style={{gridTemplateColumns:'repeat(5,1fr)'}}>
        <div className="stat-card success"><div className="stat-label">Met</div><div className="stat-value">45</div></div>
        <div className="stat-card warning"><div className="stat-label">Due</div><div className="stat-value">18</div></div>
        <div className="stat-card danger"><div className="stat-label">Overdue</div><div className="stat-value">8</div></div>
        <div className="stat-card" style={{position:'relative'}}><div style={{position:'absolute',top:0,left:0,right:0,height:2,background:'linear-gradient(90deg,#ef4444,#dc2626)'}}></div><div className="stat-label">Breached</div><div className="stat-value">5</div></div>
        <div className="stat-card purple"><div className="stat-label">Closed</div><div className="stat-value">24</div></div>
      </div>

      <div className="tabs" style={{marginBottom:0}}>
        <div className={`tab ${mainTab==='financial'?'active':''}`} onClick={()=>setMainTab('financial')}>Financial Covenants</div>
        <div className={`tab ${mainTab==='nonfinancial'?'active':''}`} onClick={()=>setMainTab('nonfinancial')}>Non-Financial Covenants</div>
      </div>

      {mainTab === 'financial' && (
        <>
          <div className="tabs" style={{borderTop:'none'}}>
            <div className={`tab ${subTab==='all'?'active':''}`} onClick={()=>setSubTab('all')}>All Covenants<span className="tab-count">{financialItems.length}</span></div>
            <div className={`tab ${subTab==='pending'?'active':''}`} onClick={()=>setSubTab('pending')}>Pending<span className="tab-count">{financialItems.filter(i=>['due','overdue'].includes(i.status)).length}</span></div>
            <div className={`tab ${subTab==='violated'?'active':''}`} onClick={()=>setSubTab('violated')}>Violated (Breach)<span className="tab-count" style={{background:'rgba(239,68,68,0.15)',color:'#f87171'}}>{financialItems.filter(i=>i.status==='breached').length}</span></div>
          </div>
          <div className="card" style={{padding:0,overflow:'hidden'}}>
            <table className="data-table">
              <thead>
                <tr><th>Customer</th><th>Covenant</th><th>Period</th><th>Due Date</th><th>Calculated Value</th><th>Threshold</th><th>Status</th><th>Breach Prob.</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {filtered.map(it => (
                  <tr key={it.id}>
                    <td style={{fontWeight:500,fontSize:13}}>{it.customer}</td>
                    <td style={{fontSize:12}}>{it.covenant}</td>
                    <td style={{fontSize:12,color:'var(--text-secondary)'}}>{it.period}</td>
                    <td style={{fontSize:12,color:'var(--text-secondary)'}}>{it.due}</td>
                    <td style={{fontFamily:'monospace',fontSize:13,fontWeight:600}}>{it.calculated}</td>
                    <td style={{fontFamily:'monospace',fontSize:12,color:'var(--text-muted)'}}>{it.threshold}</td>
                    <td><span className={`badge-status ${it.status}`}><span className="dot"/>{it.status}</span></td>
                    <td>
                      <div style={{display:'flex',alignItems:'center',gap:6}}>
                        <div className="progress-bar" style={{width:60,height:4}}>
                          <div className="fill" style={{width:`${it.probability}%`,background:it.probability>75?'var(--danger)':it.probability>40?'var(--warning)':'var(--success)'}}/>
                        </div>
                        <span style={{fontSize:11,fontWeight:600,color:it.probability>75?'var(--danger)':it.probability>40?'var(--warning)':'var(--success)'}}>{it.probability}%</span>
                      </div>
                    </td>
                    <td>
                      <div style={{display:'flex',gap:4}}>
                        {it.status==='breached' && <button className="btn btn-danger btn-sm" onClick={()=>setShowWaiver(true)}>Waive/Not Waive</button>}
                        {it.status==='due' && <button className="btn btn-ghost btn-sm"><Eye size={14}/></button>}
                        {it.status==='met' && <button className="btn btn-ghost btn-sm"><CheckCircle2 size={14}/></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {mainTab === 'nonfinancial' && (
        <>
          <div className="tabs" style={{borderTop:'none'}}>
            <div className="tab active">All Covenants<span className="tab-count">{nonFinancialItems.length}</span></div>
            <div className="tab">Pending<span className="tab-count">2</span></div>
          </div>
          <div className="card" style={{padding:0,overflow:'hidden'}}>
            <table className="data-table">
              <thead><tr><th>Customer</th><th>Item Name</th><th>Type</th><th>Period</th><th>Due Date</th><th>Status</th><th>Actions</th></tr></thead>
              <tbody>
                {nonFinancialItems.map(it => (
                  <tr key={it.id}>
                    <td style={{fontWeight:500,fontSize:13}}>{it.customer}</td>
                    <td style={{fontSize:12}}>{it.item}</td>
                    <td><span style={{fontSize:11,padding:'2px 8px',background:'var(--bg-input)',borderRadius:4}}>{it.type}</span></td>
                    <td style={{fontSize:12,color:'var(--text-secondary)'}}>{it.period}</td>
                    <td style={{fontSize:12,color:'var(--text-secondary)'}}>{it.due}</td>
                    <td><span className={`badge-status ${it.status}`}><span className="dot"/>{it.status}</span></td>
                    <td>
                      <div style={{display:'flex',gap:4}}>
                        {it.status==='submitted' && <button className="btn btn-primary btn-sm"><Shield size={13}/>Verify</button>}
                        {it.status==='due' && <button className="btn btn-ghost btn-sm"><FileText size={14}/>Upload</button>}
                        {it.status==='rejected' && <button className="btn btn-warning btn-sm" style={{background:'var(--warning)',color:'#000'}}>Resubmit</button>}
                        {it.status==='overdue' && <button className="btn btn-danger btn-sm" onClick={()=>setShowWaiver(true)}>Waive</button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* Waiver Modal */}
      {showWaiver && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:580,maxHeight:'85vh',overflow:'auto'}}>
            <div className="card-header">
              <div className="card-title">Waive Covenant — DSCR ≥ 1.5x</div>
              <button className="btn btn-ghost btn-sm" onClick={()=>setShowWaiver(false)}>✕</button>
            </div>
            <div style={{padding:'12px 16px',background:'rgba(239,68,68,0.08)',borderRadius:8,marginBottom:16,fontSize:13}}>
              <div style={{fontWeight:600,color:'var(--danger)',marginBottom:4}}>⚠ Covenant Breached</div>
              <div style={{color:'var(--text-secondary)'}}>Calculated value <strong>1.38x</strong> is below threshold <strong>≥ 1.5x</strong> for DP World Limited — Q4 2025</div>
            </div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Waiver Scope</label>
              <div style={{display:'flex',gap:12}}>
                <label style={{display:'flex',alignItems:'center',gap:6,fontSize:13,cursor:'pointer',padding:'8px 16px',background:'var(--bg-input)',border:'2px solid var(--accent)',borderRadius:8,color:'var(--accent)'}}>
                  <input type="radio" name="scope" defaultChecked/> Single Instance (This Period)
                </label>
                <label style={{display:'flex',alignItems:'center',gap:6,fontSize:13,cursor:'pointer',padding:'8px 16px',background:'var(--bg-input)',border:'1px solid var(--border-subtle)',borderRadius:8}}>
                  <input type="radio" name="scope"/> Permanent Waiver
                </label>
              </div>
            </div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Comments / Justification *</label>
              <textarea className="input" rows={3} placeholder="Provide reason for waiving this covenant..."/>
            </div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Select Letter Template</label>
              <select className="input">
                <option>Standard Waiver Letter — Financial</option>
                <option>Custom Waiver Letter — DSCR Breach</option>
              </select>
            </div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Recipients</label>
              <div style={{display:'flex',gap:6,flexWrap:'wrap'}}>
                {['CFO — James K. (james@dpworld.com)','Legal — Sarah M. (sarah@dpworld.com)'].map((r,i) => (
                  <span key={i} style={{fontSize:11,padding:'4px 10px',background:'var(--bg-input)',borderRadius:20,border:'1px solid var(--border-subtle)'}}>{r}</span>
                ))}
                <button className="btn btn-ghost btn-sm" style={{fontSize:11}}>+ Add Contact</button>
              </div>
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end'}}>
              <button className="btn btn-secondary" onClick={()=>setShowWaiver(false)}>Cancel</button>
              <button className="btn btn-secondary"><Eye size={14}/>Preview Letter</button>
              <button className="btn btn-primary"><Send size={14}/>Generate & Send</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
