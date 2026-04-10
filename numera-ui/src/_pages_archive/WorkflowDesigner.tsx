import { useState } from 'react'
import { Plus, Play, Pause, Clock, CheckCircle2, ArrowRight, GitBranch, Users, AlertTriangle, Settings, Eye, Edit, Copy } from 'lucide-react'

const workflows = [
  {id:1,name:'Spread Approval — Standard',steps:3,type:'Spreading',status:'Active',instances:42,avgTime:'4.2 hrs'},
  {id:2,name:'Spread Approval — High Value (>$10M)',steps:5,type:'Spreading',status:'Active',instances:8,avgTime:'12.6 hrs'},
  {id:3,name:'Non-Financial Document Verification',steps:2,type:'Covenant',status:'Active',instances:23,avgTime:'2.1 hrs'},
  {id:4,name:'Covenant Waiver Processing',steps:4,type:'Covenant',status:'Active',instances:5,avgTime:'24.3 hrs'},
  {id:5,name:'User Account Approval',steps:2,type:'Admin',status:'Draft',instances:0,avgTime:'—'},
]

const activeInstances = [
  {id:101,workflow:'Spread Approval — Standard',item:'Emirates NBD — Q4 2025',step:'Manager Review',assignee:'Sarah K.',sla:'2h remaining',status:'on-track'},
  {id:102,workflow:'Spread Approval — High Value',item:'HSBC — FY 2025',step:'VP Approval',assignee:'Emma W.',sla:'Overdue 4h',status:'overdue'},
  {id:103,workflow:'Covenant Waiver Processing',item:'DP World — DSCR Waiver',step:'Senior Manager Review',assignee:'Emma W.',sla:'6h remaining',status:'on-track'},
  {id:104,workflow:'Non-Financial Verification',item:'ADNOC — Insurance Cert',step:'Checker Review',assignee:'Sarah K.',sla:'1d remaining',status:'on-track'},
]

export default function WorkflowDesigner() {
  const [showDesigner, setShowDesigner] = useState(false)
  const [tab, setTab] = useState<'definitions'|'active'|'history'>('definitions')

  return (
    <>
      <div className="page-header">
        <h1>Workflow Designer</h1>
        <p>Configure approval hierarchies and track active workflow instances</p>
      </div>

      <div className="tabs">
        <div className={`tab ${tab==='definitions'?'active':''}`} onClick={()=>setTab('definitions')}>Workflow Definitions<span className="tab-count">{workflows.length}</span></div>
        <div className={`tab ${tab==='active'?'active':''}`} onClick={()=>setTab('active')}>Active Instances<span className="tab-count">{activeInstances.length}</span></div>
        <div className={`tab ${tab==='history'?'active':''}`} onClick={()=>setTab('history')}>History</div>
      </div>

      {tab === 'definitions' && (
        <>
          <div className="toolbar">
            <div className="toolbar-left" />
            <div className="toolbar-right">
              <button className="btn btn-primary" onClick={()=>setShowDesigner(true)}><Plus size={16}/>Create Workflow</button>
            </div>
          </div>

          <div style={{display:'flex',flexDirection:'column',gap:12}}>
            {workflows.map(w => (
              <div key={w.id} className="card" style={{padding:16}}>
                <div style={{display:'flex',alignItems:'center',gap:16}}>
                  <div style={{width:40,height:40,borderRadius:10,background:w.type==='Spreading'?'rgba(59,130,246,0.1)':w.type==='Covenant'?'rgba(139,92,246,0.1)':'rgba(245,158,11,0.1)',display:'flex',alignItems:'center',justifyContent:'center'}}>
                    <GitBranch size={20} style={{color:w.type==='Spreading'?'var(--accent)':w.type==='Covenant'?'var(--purple)':'var(--warning)'}}/>
                  </div>
                  <div style={{flex:1}}>
                    <div style={{fontSize:14,fontWeight:600}}>{w.name}</div>
                    <div style={{fontSize:12,color:'var(--text-muted)',marginTop:2}}>
                      {w.steps} steps · {w.type} · {w.instances} completed instances · Avg: {w.avgTime}
                    </div>
                  </div>
                  <span className={`badge-status ${w.status==='Active'?'approved':'draft'}`}><span className="dot"/>{w.status}</span>
                  <div style={{display:'flex',gap:4}}>
                    <button className="btn btn-ghost btn-sm" onClick={()=>setShowDesigner(true)}><Edit size={14}/></button>
                    <button className="btn btn-ghost btn-sm"><Copy size={14}/></button>
                    <button className="btn btn-ghost btn-sm"><Eye size={14}/></button>
                  </div>
                </div>
                {/* Visual flow preview */}
                <div style={{marginTop:12,paddingTop:12,borderTop:'1px solid var(--border-subtle)'}}>
                  <div className="workflow-flow">
                    {w.id === 1 && <>
                      <div className="workflow-node start"><Play size={14}/>Analyst Submit</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Manager Review</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><CheckCircle2 size={14}/>Final Approval</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node end"><CheckCircle2 size={14}/>Approved</div>
                    </>}
                    {w.id === 2 && <>
                      <div className="workflow-node start"><Play size={14}/>Submit</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Reviewer</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Manager</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node gateway"><GitBranch size={14}/>Value {'>'} $10M?</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>VP Approval</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node end"><CheckCircle2 size={14}/>Done</div>
                    </>}
                    {w.id === 3 && <>
                      <div className="workflow-node start"><Play size={14}/>Maker Upload</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Checker Verify</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node end"><CheckCircle2 size={14}/>Approved/Rejected</div>
                    </>}
                    {w.id === 4 && <>
                      <div className="workflow-node start"><Play size={14}/>Trigger Breach</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Manager Decision</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node gateway"><GitBranch size={14}/>Permanent?</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Sr. Manager</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node end"><CheckCircle2 size={14}/>Closed</div>
                    </>}
                    {w.id === 5 && <>
                      <div className="workflow-node start"><Play size={14}/>Registration</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node task"><Users size={14}/>Admin Review</div>
                      <div className="workflow-arrow">→</div>
                      <div className="workflow-node end"><CheckCircle2 size={14}/>Active/Rejected</div>
                    </>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'active' && (
        <div className="card" style={{padding:0,overflow:'hidden'}}>
          <table className="data-table">
            <thead><tr><th>Instance</th><th>Workflow</th><th>Current Step</th><th>Assignee</th><th>SLA</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>
              {activeInstances.map(inst => (
                <tr key={inst.id}>
                  <td style={{fontWeight:500,fontSize:13}}>{inst.item}</td>
                  <td style={{fontSize:12,color:'var(--text-secondary)'}}>{inst.workflow}</td>
                  <td>
                    <span style={{fontSize:12,padding:'3px 10px',background:'rgba(59,130,246,0.1)',borderRadius:20,color:'var(--accent)',fontWeight:500}}>
                      {inst.step}
                    </span>
                  </td>
                  <td>
                    <div style={{display:'flex',alignItems:'center',gap:6}}>
                      <div className="avatar avatar-sm">{inst.assignee.split(' ').map(n=>n[0]).join('')}</div>
                      <span style={{fontSize:12}}>{inst.assignee}</span>
                    </div>
                  </td>
                  <td>
                    <div style={{display:'flex',alignItems:'center',gap:4,fontSize:12}}>
                      <Clock size={13} style={{color:inst.status==='overdue'?'var(--danger)':'var(--text-muted)'}}/>
                      <span style={{color:inst.status==='overdue'?'var(--danger)':'var(--text-secondary)',fontWeight:inst.status==='overdue'?600:400}}>{inst.sla}</span>
                    </div>
                  </td>
                  <td>
                    {inst.status === 'overdue' ? (
                      <span className="badge-status overdue"><span className="dot"/>Overdue</span>
                    ) : (
                      <span className="badge-status approved"><span className="dot"/>On Track</span>
                    )}
                  </td>
                  <td>
                    <div style={{display:'flex',gap:4}}>
                      <button className="btn btn-ghost btn-sm"><Eye size={14}/></button>
                      {inst.status === 'overdue' && <button className="btn btn-danger btn-sm"><AlertTriangle size={13}/>Escalate</button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'history' && (
        <div className="empty-state">
          <Clock size={48}/>
          <h3>Workflow History</h3>
          <p>Completed workflow instances will appear here with full audit trails showing each step's actor, action, timestamp, and comments.</p>
        </div>
      )}

      {/* Workflow Designer Modal */}
      {showDesigner && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:800,maxHeight:'90vh',overflow:'auto'}}>
            <div className="card-header">
              <div className="card-title">Workflow Designer — Spread Approval</div>
              <button className="btn btn-ghost btn-sm" onClick={()=>setShowDesigner(false)}>✕</button>
            </div>

            <div className="grid-2" style={{marginBottom:20}}>
              <div className="input-group"><label>Workflow Name *</label><input className="input" defaultValue="Spread Approval — Standard"/></div>
              <div className="input-group"><label>Type *</label><select className="input"><option>Spreading</option><option>Covenant</option><option>Admin</option></select></div>
            </div>

            <div style={{fontSize:12,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:12}}>Workflow Steps</div>

            <div className="workflow-canvas" style={{flexDirection:'column',gap:16,alignItems:'stretch',padding:20}}>
              {[
                {step:1,name:'Analyst Submission',role:'Analyst',sla:'—',type:'start'},
                {step:2,name:'Manager Review',role:'Manager',sla:'8 hours',type:'task'},
                {step:3,name:'Final Approval',role:'Global Manager',sla:'24 hours',type:'task'},
              ].map((s,i) => (
                <div key={i} style={{display:'flex',alignItems:'center',gap:12}}>
                  <div style={{width:28,height:28,borderRadius:'50%',background:s.type==='start'?'rgba(16,185,129,0.2)':'rgba(59,130,246,0.2)',display:'flex',alignItems:'center',justifyContent:'center',fontSize:12,fontWeight:700,color:s.type==='start'?'var(--success)':'var(--accent)',flexShrink:0}}>{s.step}</div>
                  <div style={{flex:1,display:'flex',gap:8}}>
                    <input className="input" style={{flex:1}} defaultValue={s.name}/>
                    <select className="input" style={{width:150}} defaultValue={s.role}>
                      <option>Analyst</option><option>Manager</option><option>Global Manager</option><option>VP</option>
                    </select>
                    <input className="input" style={{width:100}} defaultValue={s.sla} placeholder="SLA"/>
                  </div>
                  <button className="btn btn-ghost btn-sm" style={{color:'var(--danger)'}}><span>✕</span></button>
                </div>
              ))}
              <button className="btn btn-secondary" style={{alignSelf:'flex-start',marginLeft:40}}><Plus size={14}/>Add Step</button>
            </div>

            <div style={{marginTop:16}}>
              <div style={{fontSize:12,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:8}}>Conditions (Optional)</div>
              <div style={{display:'flex',gap:8,alignItems:'center',padding:12,background:'var(--bg-input)',borderRadius:8,border:'1px solid var(--border-subtle)'}}>
                <span style={{fontSize:12,color:'var(--text-secondary)'}}>If</span>
                <select className="input" style={{width:160}}><option>Spread Amount</option><option>Customer Type</option><option>Covenant Type</option></select>
                <select className="input" style={{width:80}}><option>{'>'}</option><option>{'<'}</option><option>=</option></select>
                <input className="input" style={{width:120}} placeholder="Value"/>
                <span style={{fontSize:12,color:'var(--text-secondary)'}}>then add step:</span>
                <input className="input" style={{width:160}} placeholder="VP Approval"/>
              </div>
            </div>

            <div style={{marginTop:16}}>
              <div style={{fontSize:12,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:8}}>Escalation Rules</div>
              <div style={{display:'flex',gap:8,alignItems:'center',padding:12,background:'var(--bg-input)',borderRadius:8,border:'1px solid var(--border-subtle)'}}>
                <span style={{fontSize:12,color:'var(--text-secondary)'}}>If not actioned within SLA, auto-escalate to</span>
                <select className="input" style={{width:160}}><option>Next Level Manager</option><option>Global Manager</option><option>Admin</option></select>
              </div>
            </div>

            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:20}}>
              <button className="btn btn-secondary" onClick={()=>setShowDesigner(false)}>Cancel</button>
              <button className="btn btn-secondary"><Eye size={14}/>Preview</button>
              <button className="btn btn-primary" onClick={()=>setShowDesigner(false)}><CheckCircle2 size={14}/>Save & Publish</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
