import { useNavigate } from 'react-router-dom'
import { Plus, FileSpreadsheet, Eye, Copy, Edit, ArrowLeft, Building2, Clock } from 'lucide-react'

const items = [
  {id:1,date:'31 Dec 2025',freq:'Annual',audit:'Audited',type:'Original',status:'approved',accuracy:94,time:'2m 14s',analyst:'John Doe',base:true},
  {id:2,date:'30 Jun 2025',freq:'Semi-Annual',audit:'Reviewed',type:'Original',status:'submitted',accuracy:91,time:'3m 05s',analyst:'John Doe',base:false},
  {id:3,date:'31 Mar 2025',freq:'Quarterly',audit:'Unaudited',type:'Original',status:'approved',accuracy:88,time:'2m 48s',analyst:'Sarah K.',base:false},
  {id:4,date:'31 Dec 2024',freq:'Annual',audit:'Audited',type:'Original',status:'approved',accuracy:92,time:'3m 22s',analyst:'John Doe',base:false,migrated:true},
  {id:5,date:'30 Jun 2024',freq:'Semi-Annual',audit:'Reviewed',type:'Original',status:'approved',accuracy:89,time:'2m 56s',analyst:'Sarah K.',base:false,migrated:true},
]

export default function ExistingItems() {
  const nav = useNavigate()
  return (
    <>
      <div style={{display:'flex',alignItems:'center',gap:12,marginBottom:24}}>
        <button className="btn btn-ghost" onClick={()=>nav('/customers')}><ArrowLeft size={16}/></button>
        <div>
          <div style={{display:'flex',alignItems:'center',gap:8}}>
            <Building2 size={20} style={{color:'var(--accent)'}}/>
            <h1 style={{fontSize:22,fontWeight:700}}>Emirates NBD PJSC</h1>
            <span style={{fontSize:12,fontFamily:'monospace',color:'var(--text-muted)',background:'var(--bg-input)',padding:'2px 8px',borderRadius:4}}>ENT-10234</span>
          </div>
          <p style={{fontSize:14,color:'var(--text-secondary)',marginTop:2}}>Financial Year End: 31 December &nbsp;|&nbsp; Currency: AED &nbsp;|&nbsp; Group: UAE Banking</p>
        </div>
        <div style={{flex:1}}/>
        <button className="btn btn-primary"><Plus size={16}/>Add Item</button>
      </div>

      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Statement Date</th>
              <th>Frequency</th>
              <th>Audit Method</th>
              <th>Spread Type</th>
              <th>Status</th>
              <th>AI Accuracy</th>
              <th>Processing Time</th>
              <th>Analyst</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.map(it => (
              <tr key={it.id}>
                <td>
                  <div style={{display:'flex',alignItems:'center',gap:8}}>
                    <FileSpreadsheet size={16} style={{color: it.status==='approved'?'var(--success)':it.status==='submitted'?'var(--accent)':'var(--text-muted)',flexShrink:0}}/>
                    <span style={{fontWeight:600}}>{it.date}</span>
                    {it.migrated && <span style={{fontSize:9,background:'rgba(139,92,246,0.15)',color:'#a78bfa',padding:'1px 6px',borderRadius:3,fontWeight:600}}>Migrated from CL</span>}
                    {it.base && <span style={{fontSize:9,background:'rgba(59,130,246,0.15)',color:'#60a5fa',padding:'1px 6px',borderRadius:3,fontWeight:600}}>BASE</span>}
                  </div>
                </td>
                <td style={{fontSize:12}}>{it.freq}</td>
                <td style={{fontSize:12}}>{it.audit}</td>
                <td style={{fontSize:12}}>{it.type}</td>
                <td><span className={`badge-status ${it.status}`}><span className="dot"/>{it.status}</span></td>
                <td><span className={`confidence ${it.accuracy>=90?'high':it.accuracy>=70?'medium':'low'}`}>{it.accuracy}%</span></td>
                <td style={{fontFamily:'monospace',fontSize:12,color:'var(--text-secondary)'}}><Clock size={12} style={{display:'inline',verticalAlign:-1,marginRight:4}}/>{it.time}</td>
                <td style={{fontSize:12,color:'var(--text-secondary)'}}>{it.analyst}</td>
                <td>
                  <div style={{display:'flex',gap:4}}>
                    <button className="btn btn-primary btn-sm" onClick={()=>nav('/workspace')}>
                      <FileSpreadsheet size={13}/>Spread
                    </button>
                    <button className="btn btn-ghost btn-sm" title="Read Only"><Eye size={14}/></button>
                    <button className="btn btn-ghost btn-sm" title="Duplicate"><Copy size={14}/></button>
                    <button className="btn btn-ghost btn-sm" title="Override"><Edit size={14}/></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Version History Preview */}
      <div className="card" style={{marginTop:20}}>
        <div className="card-header">
          <div className="card-title">Version History — 31 Dec 2025</div>
          <button className="btn btn-ghost btn-sm">View Full History</button>
        </div>
        <div style={{display:'flex',flexDirection:'column',gap:12}}>
          {[
            {ver:'v3',action:'APPROVED',by:'Manager — Sarah K.',time:'2026-04-08 14:32',comment:'Approved after validation check'},
            {ver:'v2',action:'SUBMITTED',by:'Analyst — John Doe',time:'2026-04-08 11:15',comment:'All validations passed. BS balanced.'},
            {ver:'v1',action:'DRAFT_SAVED',by:'Analyst — John Doe',time:'2026-04-08 10:02',comment:'Initial AI mapping — 94% auto-accepted'},
          ].map((v,i) => (
            <div key={i} style={{display:'flex',alignItems:'center',gap:16,padding:'10px 14px',background:'var(--bg-input)',borderRadius:8,fontSize:13}}>
              <span style={{fontFamily:'monospace',fontWeight:700,color:'var(--accent)',width:28}}>{v.ver}</span>
              <span className={`badge-status ${v.action==='APPROVED'?'approved':v.action==='SUBMITTED'?'submitted':'draft'}`}><span className="dot"/>{v.action}</span>
              <span style={{color:'var(--text-secondary)',flex:1}}>{v.by}</span>
              <span style={{color:'var(--text-muted)',fontSize:12,fontFamily:'monospace'}}>{v.time}</span>
              <span style={{color:'var(--text-secondary)',fontSize:12,fontStyle:'italic',maxWidth:300,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>"{v.comment}"</span>
              <button className="btn btn-ghost btn-sm">Diff</button>
            </div>
          ))}
        </div>
      </div>
    </>
  )
}
