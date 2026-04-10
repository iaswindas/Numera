import { useNavigate } from 'react-router-dom'
import { Search, Plus, Edit, Eye, Building2 } from 'lucide-react'

const customers = [
  {id:'C001',entityId:'ENT-10234',name:'Emirates NBD PJSC',yearEnd:'31 Dec',currency:'AED',group:'UAE Banking',items:12,status:'Active'},
  {id:'C002',entityId:'ENT-10567',name:'HSBC Holdings PLC',yearEnd:'31 Dec',currency:'GBP',group:'Europe Banking',items:8,status:'Active'},
  {id:'C003',entityId:'ENT-10891',name:'Unilever PLC',yearEnd:'31 Dec',currency:'EUR',group:'Europe Corporate',items:6,status:'Active'},
  {id:'C004',entityId:'ENT-11023',name:'DP World Limited',yearEnd:'31 Dec',currency:'USD',group:'UAE Corporate',items:10,status:'Active'},
  {id:'C005',entityId:'ENT-11456',name:'ADNOC Distribution',yearEnd:'31 Dec',currency:'AED',group:'UAE Corporate',items:4,status:'Active'},
  {id:'C006',entityId:'ENT-11789',name:'BP PLC',yearEnd:'31 Dec',currency:'GBP',group:'Europe Corporate',items:7,status:'Active'},
  {id:'C007',entityId:'ENT-12034',name:'Saudi Aramco',yearEnd:'31 Dec',currency:'SAR',group:'GCC Corporate',items:3,status:'Active'},
  {id:'C008',entityId:'ENT-12567',name:'Mashreq Bank PSC',yearEnd:'31 Dec',currency:'AED',group:'UAE Banking',items:9,status:'Inactive'},
]

export default function CustomerSearch() {
  const nav = useNavigate()
  return (
    <>
      <div className="page-header">
        <h1>Search Customer</h1>
        <p>Search and manage customer records for financial spreading</p>
      </div>

      <div className="card" style={{marginBottom:20}}>
        <div style={{display:'flex',gap:12,alignItems:'flex-end'}}>
          <div className="input-group" style={{flex:1}}>
            <label>Customer Long Name</label>
            <input className="input" placeholder="Search by customer name..." />
          </div>
          <div className="input-group" style={{width:200}}>
            <label>Entity ID</label>
            <input className="input" placeholder="ENT-XXXXX" />
          </div>
          <div className="input-group" style={{width:180}}>
            <label>Group</label>
            <select className="input">
              <option>All Groups</option>
              <option>UAE Banking</option>
              <option>UAE Corporate</option>
              <option>Europe Banking</option>
              <option>Europe Corporate</option>
              <option>GCC Corporate</option>
            </select>
          </div>
          <button className="btn btn-primary" style={{height:38}}><Search size={16}/>Search</button>
          <button className="btn btn-secondary" style={{height:38}}><Plus size={16}/>New Customer</button>
        </div>
      </div>

      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Customer Name</th>
              <th>Entity ID</th>
              <th>Year End</th>
              <th>Currency</th>
              <th>Group</th>
              <th>Spread Items</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {customers.map(c => (
              <tr key={c.id}>
                <td>
                  <div style={{display:'flex',alignItems:'center',gap:8,cursor:'pointer'}} onClick={()=>nav(`/customers/${c.id}/items`)}>
                    <Building2 size={16} style={{color:'var(--accent)',flexShrink:0}}/>
                    <span style={{fontWeight:600,color:'var(--accent)'}}>{c.name}</span>
                  </div>
                </td>
                <td style={{fontFamily:'monospace',fontSize:12,color:'var(--text-secondary)'}}>{c.entityId}</td>
                <td style={{fontSize:13}}>{c.yearEnd}</td>
                <td><span style={{fontSize:12,fontWeight:600,padding:'2px 8px',background:'var(--bg-input)',borderRadius:4}}>{c.currency}</span></td>
                <td style={{fontSize:12,color:'var(--text-secondary)'}}>{c.group}</td>
                <td style={{fontWeight:600}}>{c.items}</td>
                <td><span className={`badge-status ${c.status==='Active'?'approved':'draft'}`}><span className="dot"/>{c.status}</span></td>
                <td>
                  <div style={{display:'flex',gap:4}}>
                    <button className="btn btn-ghost btn-sm" onClick={()=>nav(`/customers/${c.id}/items`)}><Eye size={14}/>View Items</button>
                    <button className="btn btn-ghost btn-sm"><Edit size={14}/></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
