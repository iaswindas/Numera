import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Search, Shield, Eye, Edit, ToggleLeft, ToggleRight, Building2 } from 'lucide-react'

const customers = [
  {id:1,name:'Emirates NBD PJSC',rimId:'RIM-10234',modified:'2026-04-08',covenants:8,financial:5,nonFinancial:3,status:'Active'},
  {id:2,name:'HSBC Holdings PLC',rimId:'RIM-10567',modified:'2026-04-06',covenants:12,financial:7,nonFinancial:5,status:'Active'},
  {id:3,name:'DP World Limited',rimId:'RIM-11023',modified:'2026-04-05',covenants:6,financial:4,nonFinancial:2,status:'Active'},
  {id:4,name:'ADNOC Distribution',rimId:'RIM-11456',modified:'2026-04-03',covenants:4,financial:2,nonFinancial:2,status:'Active'},
  {id:5,name:'Saudi Aramco',rimId:'RIM-12034',modified:'2026-03-28',covenants:10,financial:6,nonFinancial:4,status:'Active'},
  {id:6,name:'Mashreq Bank PSC',rimId:'RIM-12567',modified:'2026-03-15',covenants:3,financial:2,nonFinancial:1,status:'Inactive'},
]

export default function CovenantManagement() {
  const nav = useNavigate()
  const [showCreate, setShowCreate] = useState(false)

  return (
    <>
      <div className="page-header">
        <h1>Covenant Management</h1>
        <p>Manage covenant customers and covenant definitions</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar"><Search size={16}/><input placeholder="Search by RIM ID or customer name..."/></div>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={()=>setShowCreate(true)}><Plus size={16}/>New Customer</button>
        </div>
      </div>

      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Customer Name</th>
              <th>RIM ID</th>
              <th>Last Modified</th>
              <th>Financial</th>
              <th>Non-Financial</th>
              <th>Total Covenants</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {customers.map(c => (
              <tr key={c.id}>
                <td>
                  <div style={{display:'flex',alignItems:'center',gap:8}}>
                    <Shield size={16} style={{color:'var(--accent)',flexShrink:0}}/>
                    <span style={{fontWeight:600,color:'var(--accent)',cursor:'pointer'}}>{c.name}</span>
                  </div>
                </td>
                <td style={{fontFamily:'monospace',fontSize:12,color:'var(--text-secondary)'}}>{c.rimId}</td>
                <td style={{fontSize:12,color:'var(--text-secondary)'}}>{c.modified}</td>
                <td style={{fontWeight:600,color:'var(--accent)'}}>{c.financial}</td>
                <td style={{fontWeight:600,color:'var(--purple)'}}>{c.nonFinancial}</td>
                <td style={{fontWeight:700}}>{c.covenants}</td>
                <td>
                  <span className={`badge-status ${c.status==='Active'?'approved':'draft'}`}>
                    <span className="dot"/>{c.status}
                  </span>
                </td>
                <td>
                  <div style={{display:'flex',gap:4}}>
                    <button className="btn btn-ghost btn-sm"><Edit size={14}/></button>
                    <button className="btn btn-primary btn-sm"><Plus size={13}/>Add Covenant</button>
                    <button className="btn btn-ghost btn-sm" onClick={()=>nav('/covenant-items')}><Eye size={14}/>View</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create Customer Modal */}
      {showCreate && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:640,maxHeight:'85vh',overflow:'auto'}}>
            <div className="card-header">
              <div className="card-title">Create Covenant Customer</div>
              <button className="btn btn-ghost btn-sm" onClick={()=>setShowCreate(false)}>✕</button>
            </div>
            <div style={{fontSize:12,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:12}}>Basic Information</div>
            <div className="grid-2" style={{marginBottom:16}}>
              <div className="input-group"><label>Customer Name *</label><input className="input" placeholder="Enter customer name"/></div>
              <div className="input-group"><label>RIM ID *</label><input className="input" placeholder="RIM-XXXXX"/></div>
              <div className="input-group"><label>CL Entity ID</label><input className="input" placeholder="ENT-XXXXX"/></div>
              <div className="input-group"><label>Financial Year End *</label><input className="input" type="date"/></div>
            </div>
            <div style={{fontSize:12,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:12,marginTop:20}}>Additional Information — Contacts</div>
            <div className="tabs">
              <div className="tab active">Internal Users</div>
              <div className="tab">External Users</div>
            </div>
            <div style={{display:'flex',gap:8,marginBottom:12}}>
              <input className="input" style={{flex:1}} placeholder="Search by username (min 4 chars)..."/>
              <button className="btn btn-secondary"><Search size={14}/>Search</button>
              <button className="btn btn-primary"><Plus size={14}/>Add User</button>
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:20}}>
              <button className="btn btn-secondary" onClick={()=>setShowCreate(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={()=>setShowCreate(false)}>Submit</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
