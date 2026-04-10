import { Plus, Edit, Trash2, Search, Eye, Calculator } from 'lucide-react'
import { useState } from 'react'

const formulas = [
  {id:1,name:'Debt Service Coverage Ratio',expr:'(EBITDA) / (Interest + Principal Repayment)',groups:['UAE Banking','GCC Corporate'],status:'Active',modified:'2026-04-05'},
  {id:2,name:'Net Debt / EBITDA',expr:'(Total Debt - Cash) / EBITDA',groups:['UAE Banking','Europe Banking'],status:'Active',modified:'2026-04-02'},
  {id:3,name:'Interest Coverage Ratio',expr:'EBIT / Interest Expense',groups:['All Groups'],status:'Active',modified:'2026-03-28'},
  {id:4,name:'Current Ratio',expr:'Current Assets / Current Liabilities',groups:['All Groups'],status:'Active',modified:'2026-03-20'},
  {id:5,name:'Debt to Equity',expr:'Total Debt / Total Equity',groups:['All Groups'],status:'Active',modified:'2026-03-15'},
  {id:6,name:'NPL Ratio',expr:'Non-Performing Loans / Total Loans',groups:['UAE Banking','Europe Banking'],status:'Inactive',modified:'2026-02-10'},
]

export default function FormulaManagement() {
  const [showBuilder, setShowBuilder] = useState(false)
  return (
    <>
      <div className="page-header">
        <h1>Formula Management</h1>
        <p>Create and manage standard formulas for covenant calculations</p>
      </div>
      <div className="toolbar">
        <div className="toolbar-left"><div className="search-bar"><Search size={16}/><input placeholder="Search formulas..."/></div></div>
        <div className="toolbar-right"><button className="btn btn-primary" onClick={()=>setShowBuilder(true)}><Plus size={16}/>Add Formula</button></div>
      </div>
      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead><tr><th>Formula Name</th><th>Expression</th><th>Applicable Groups</th><th>Status</th><th>Last Modified</th><th>Actions</th></tr></thead>
          <tbody>
            {formulas.map(f => (
              <tr key={f.id}>
                <td style={{fontWeight:600}}><Calculator size={14} style={{display:'inline',verticalAlign:-2,marginRight:6,color:'var(--accent)'}}/>{f.name}</td>
                <td><code style={{fontSize:11,color:'var(--cyan)',background:'var(--bg-input)',padding:'2px 8px',borderRadius:4}}>{f.expr}</code></td>
                <td><div style={{display:'flex',gap:4,flexWrap:'wrap'}}>{f.groups.map((g,i)=><span key={i} style={{fontSize:10,padding:'2px 8px',background:'var(--bg-input)',borderRadius:10,border:'1px solid var(--border-subtle)'}}>{g}</span>)}</div></td>
                <td><span className={`badge-status ${f.status==='Active'?'approved':'draft'}`}><span className="dot"/>{f.status}</span></td>
                <td style={{fontSize:12,color:'var(--text-secondary)'}}>{f.modified}</td>
                <td><div style={{display:'flex',gap:4}}><button className="btn btn-ghost btn-sm"><Edit size={14}/></button><button className="btn btn-ghost btn-sm"><Eye size={14}/></button><button className="btn btn-ghost btn-sm" style={{color:'var(--danger)'}}><Trash2 size={14}/></button></div></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showBuilder && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:640}}>
            <div className="card-header"><div className="card-title">Formula Builder</div><button className="btn btn-ghost btn-sm" onClick={()=>setShowBuilder(false)}>✕</button></div>
            <div className="input-group" style={{marginBottom:16}}><label>Formula Name *</label><input className="input" placeholder="e.g. Debt Service Coverage Ratio"/></div>
            <div className="input-group" style={{marginBottom:16}}>
              <label>Formula Expression</label>
              <div className="formula-display">(EBITDA) / (Interest Expense + Principal Repayment)</div>
              <div className="formula-ops">
                {['Revenue','EBITDA','Total Debt','Cash','Interest','Current Assets','Current Liabilities','Total Equity'].map((v,i)=><div key={i} className="formula-op">{v}</div>)}
              </div>
              <div className="formula-ops" style={{marginTop:8}}>
                {['+','−','×','÷','(',')'].map((v,i)=><div key={i} className="formula-op" style={{fontWeight:700,color:'var(--accent)',width:36,textAlign:'center'}}>{v}</div>)}
              </div>
            </div>
            <div className="input-group" style={{marginBottom:16}}><label>Applicable Groups</label>
              <div style={{display:'flex',gap:8,flexWrap:'wrap'}}>{['UAE Banking','UAE Corporate','Europe Banking','Europe Corporate','GCC Corporate'].map((g,i)=><label key={i} style={{display:'flex',alignItems:'center',gap:4,fontSize:12,cursor:'pointer'}}><input type="checkbox" defaultChecked={i<2}/>{g}</label>)}</div>
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end'}}><button className="btn btn-secondary" onClick={()=>setShowBuilder(false)}>Cancel</button><button className="btn btn-primary" onClick={()=>setShowBuilder(false)}>Submit</button></div>
          </div>
        </div>
      )}
    </>
  )
}
