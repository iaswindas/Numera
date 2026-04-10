import { useState } from 'react'
import { Download, FileText, Filter, RefreshCw, Info, BarChart3, Shield } from 'lucide-react'

const spreadReports = [
  {name:'Spread Details Report',desc:'All spreads by status, date range, analyst, customer'},
  {name:'Customer Details Report',desc:'Customer information with spread history'},
  {name:'User Activity Report',desc:'Analyst productivity, login frequency, actions'},
  {name:'OCR Accuracy Report',desc:'AI extraction accuracy metrics per document type'},
  {name:'AI Performance Report',desc:'Mapping confidence distribution, correction rates'},
]
const covenantReports = [
  {name:'Covenant Pending Report',desc:'All Due/Overdue items requiring action'},
  {name:'Covenant Default Report',desc:'All breached items with details (Breach Report)'},
  {name:'Covenant History Report',desc:'Historical status changes per covenant'},
  {name:'Covenant Change History Report',desc:'All modifications to covenant definitions'},
  {name:'Non-Financial Item Report',desc:'Filterable by status — Rejected, Overdue, etc.'},
]

export default function Reports() {
  const [selected, setSelected] = useState<string|null>(null)
  return (
    <>
      <div className="page-header">
        <h1>Reports & MIS</h1>
        <p>Generate compliance, audit, and analytics reports</p>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header"><div><div className="card-title"><BarChart3 size={16} style={{display:'inline',verticalAlign:-3,marginRight:6}}/>Spreading Reports</div></div></div>
          {spreadReports.map((r,i) => (
            <div key={i} style={{display:'flex',alignItems:'center',gap:12,padding:'12px 14px',borderBottom:'1px solid var(--border-subtle)',cursor:'pointer',borderRadius:6,background:selected===r.name?'var(--accent-glow)':'transparent'}} onClick={()=>setSelected(r.name)}>
              <FileText size={16} style={{color:'var(--accent)',flexShrink:0}}/>
              <div style={{flex:1}}>
                <div style={{fontSize:13,fontWeight:500}}>{r.name}</div>
                <div style={{fontSize:11,color:'var(--text-muted)'}}>{r.desc}</div>
              </div>
              <button className="btn btn-ghost btn-sm"><Download size={14}/></button>
            </div>
          ))}
        </div>
        <div className="card">
          <div className="card-header"><div><div className="card-title"><Shield size={16} style={{display:'inline',verticalAlign:-3,marginRight:6}}/>Covenant Reports</div></div></div>
          {covenantReports.map((r,i) => (
            <div key={i} style={{display:'flex',alignItems:'center',gap:12,padding:'12px 14px',borderBottom:'1px solid var(--border-subtle)',cursor:'pointer',borderRadius:6,background:selected===r.name?'var(--accent-glow)':'transparent'}} onClick={()=>setSelected(r.name)}>
              <FileText size={16} style={{color:'var(--purple)',flexShrink:0}}/>
              <div style={{flex:1}}>
                <div style={{fontSize:13,fontWeight:500}}>{r.name}</div>
                <div style={{fontSize:11,color:'var(--text-muted)'}}>{r.desc}</div>
              </div>
              <button className="btn btn-ghost btn-sm"><Download size={14}/></button>
            </div>
          ))}
        </div>
      </div>

      {/* Filter panel */}
      {selected && (
        <div className="card" style={{marginTop:20}}>
          <div className="card-header">
            <div className="card-title"><Filter size={16} style={{display:'inline',verticalAlign:-3,marginRight:6}}/>{selected} — Filters</div>
            <div style={{display:'flex',gap:8}}>
              <button className="btn btn-ghost btn-sm"><Info size={14}/>Filter Info</button>
              <button className="btn btn-ghost btn-sm"><RefreshCw size={14}/>Reset</button>
            </div>
          </div>
          <div style={{display:'flex',gap:12,flexWrap:'wrap',marginBottom:16}}>
            <div className="input-group" style={{minWidth:180}}>
              <label>Date From</label><input className="input" type="date" defaultValue="2026-01-09"/>
            </div>
            <div className="input-group" style={{minWidth:180}}>
              <label>Date To</label><input className="input" type="date" defaultValue="2026-04-09"/>
            </div>
            <div className="input-group" style={{minWidth:160}}>
              <label>Status</label><select className="input"><option>All</option><option>Due</option><option>Overdue</option><option>Met</option><option>Breached</option><option>Closed</option></select>
            </div>
            <div className="input-group" style={{minWidth:160}}>
              <label>Customer</label><select className="input"><option>All Customers</option><option>Emirates NBD</option><option>HSBC Holdings</option><option>DP World</option></select>
            </div>
            <div className="input-group" style={{minWidth:160}}>
              <label>Group</label><select className="input"><option>All Groups</option><option>UAE Banking</option><option>Europe Banking</option></select>
            </div>
          </div>
          <div style={{display:'flex',gap:8,alignItems:'center'}}>
            <button className="btn btn-primary"><BarChart3 size={14}/>Generate Report</button>
            <span style={{color:'var(--text-muted)',fontSize:12}}>Export as:</span>
            <button className="btn btn-secondary btn-sm">📊 Excel</button>
            <button className="btn btn-secondary btn-sm">📄 PDF</button>
            <button className="btn btn-secondary btn-sm">🌐 HTML</button>
          </div>
        </div>
      )}
    </>
  )
}
