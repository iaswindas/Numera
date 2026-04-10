import { Plus, Edit, Trash2, Search, Eye, Mail } from 'lucide-react'
import { useState } from 'react'

const templates = [
  {id:1,name:'Standard Waiver Letter — Financial',category:'Financial',status:'Active',modified:'2026-04-01'},
  {id:2,name:'Not-Waive Letter — Financial',category:'Financial',status:'Active',modified:'2026-03-28'},
  {id:3,name:'Breach Notification — Non-Financial',category:'Non-Financial',status:'Active',modified:'2026-03-25'},
  {id:4,name:'Custom DSCR Breach Letter',category:'Financial',status:'Active',modified:'2026-03-20'},
  {id:5,name:'Overdue Reminder Template',category:'Both',status:'Inactive',modified:'2026-02-15'},
]

export default function EmailTemplates() {
  const [showCreate, setShowCreate] = useState(false)
  return (
    <>
      <div className="page-header">
        <h1>Email Template & Signature Management</h1>
        <p>Configure waiver and breach letter templates with dynamic field tags</p>
      </div>

      <div className="grid-2" style={{marginBottom:24}}>
        <div className="card">
          <div className="card-header">
            <div className="card-title">Letter Templates</div>
            <button className="btn btn-primary btn-sm" onClick={()=>setShowCreate(true)}><Plus size={14}/>Add New Document</button>
          </div>
          <div className="search-bar" style={{marginBottom:12,maxWidth:'100%'}}><Search size={16}/><input placeholder="Search templates..."/></div>
          {templates.map(t => (
            <div key={t.id} style={{display:'flex',alignItems:'center',gap:12,padding:'10px 14px',borderBottom:'1px solid var(--border-subtle)'}}>
              <Mail size={16} style={{color:'var(--accent)',flexShrink:0}}/>
              <div style={{flex:1}}>
                <div style={{fontSize:13,fontWeight:500}}>{t.name}</div>
                <div style={{fontSize:11,color:'var(--text-muted)'}}>{t.category} · Modified {t.modified}</div>
              </div>
              <span className={`badge-status ${t.status==='Active'?'approved':'draft'}`}><span className="dot"/>{t.status}</span>
              <div style={{display:'flex',gap:4}}>
                <button className="btn btn-ghost btn-sm"><Edit size={14}/></button>
                <button className="btn btn-ghost btn-sm"><Eye size={14}/></button>
                <button className="btn btn-ghost btn-sm" style={{color:'var(--danger)'}}><Trash2 size={14}/></button>
              </div>
            </div>
          ))}
        </div>

        <div className="card">
          <div className="card-header"><div className="card-title">Email Signature</div></div>
          <div style={{background:'var(--bg-input)',border:'1px solid var(--border-subtle)',borderRadius:8,padding:16,minHeight:200}}>
            <div style={{fontSize:14,fontWeight:600,marginBottom:4}}>John Doe</div>
            <div style={{fontSize:12,color:'var(--text-secondary)'}}>Senior Credit Analyst</div>
            <div style={{fontSize:12,color:'var(--text-secondary)'}}>Corporate Banking — MENA Region</div>
            <div style={{fontSize:12,color:'var(--accent)',marginTop:8}}>john.doe@numera-bank.com</div>
            <div style={{fontSize:12,color:'var(--text-muted)'}}>+971 4 XXX XXXX</div>
            <div style={{borderTop:'2px solid var(--accent)',marginTop:12,paddingTop:8}}>
              <div style={{fontSize:11,fontStyle:'italic',color:'var(--text-muted)'}}>This email and any attachments are confidential and intended solely for the addressee.</div>
            </div>
          </div>
          <div style={{marginTop:12,display:'flex',gap:8,justifyContent:'flex-end'}}>
            <button className="btn btn-secondary btn-sm">Edit</button>
            <button className="btn btn-primary btn-sm">Save Signature</button>
          </div>

          <div style={{marginTop:24}}>
            <div className="card-title" style={{marginBottom:12}}>Available Field Tags</div>
            <div style={{display:'flex',gap:6,flexWrap:'wrap'}}>
              {['{Customer_Name}','{RIM_ID}','{Covenant_Name}','{Period}','{Due_Date}','{Calculated_Value}','{Threshold_Value}','{Analyst_Name}','{Manager_Name}','{Date}','{Breach_Date}'].map((t,i) => (
                <span key={i} style={{fontSize:11,padding:'3px 10px',background:'rgba(59,130,246,0.1)',border:'1px solid rgba(59,130,246,0.2)',borderRadius:4,color:'var(--accent)',cursor:'grab',fontFamily:'monospace'}}>{t}</span>
              ))}
            </div>
            <div style={{fontSize:11,color:'var(--text-muted)',marginTop:8}}>Drag and drop tags into document content area</div>
          </div>
        </div>
      </div>

      {showCreate && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:720,maxHeight:'85vh',overflow:'auto'}}>
            <div className="card-header"><div className="card-title">Create New Document Template</div><button className="btn btn-ghost btn-sm" onClick={()=>setShowCreate(false)}>✕</button></div>
            <div className="grid-2" style={{marginBottom:16}}>
              <div className="input-group"><label>Template Name *</label><input className="input" placeholder="Enter template name"/></div>
              <div className="input-group"><label>Category *</label><select className="input"><option>Financial</option><option>Non-Financial</option><option>Both</option></select></div>
            </div>
            <div className="input-group" style={{marginBottom:16}}><label>Subject Line</label><input className="input" placeholder="Re: Covenant Waiver — {Customer_Name} — {Period}"/></div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Document Content</label>
              <div style={{background:'white',border:'1px solid var(--border-subtle)',borderRadius:8,minHeight:200,padding:16,color:'#1a1a1a',fontSize:13,lineHeight:1.8}}>
                <p>Dear Sir/Madam,</p>
                <p style={{marginTop:8}}>This is to inform you that the covenant <strong style={{color:'#2563eb'}}>{'{Covenant_Name}'}</strong> for <strong style={{color:'#2563eb'}}>{'{Customer_Name}'}</strong> (RIM: <strong style={{color:'#2563eb'}}>{'{RIM_ID}'}</strong>) has been reviewed for the period <strong style={{color:'#2563eb'}}>{'{Period}'}</strong>.</p>
                <p style={{marginTop:8}}>The calculated value of <strong style={{color:'#2563eb'}}>{'{Calculated_Value}'}</strong> has breached the threshold of <strong style={{color:'#2563eb'}}>{'{Threshold_Value}'}</strong>.</p>
                <p style={{marginTop:8}}>After due consideration, the bank has decided to waive this covenant for the current period.</p>
              </div>
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end'}}><button className="btn btn-secondary" onClick={()=>setShowCreate(false)}>Cancel</button><button className="btn btn-primary" onClick={()=>setShowCreate(false)}>Submit</button></div>
          </div>
        </div>
      )}
    </>
  )
}
