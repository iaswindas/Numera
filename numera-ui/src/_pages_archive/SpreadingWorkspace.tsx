import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Save, Send, CheckCircle2, AlertTriangle, XCircle, ZoomIn, ZoomOut,
  RotateCw, Scissors, Merge, FileText, Download, Columns, MousePointer,
  Zap, ChevronDown, Lock, Eye, MessageSquare, History, Settings, Layers,
  ChevronRight, Check, X
} from 'lucide-react'

const zones = [
  {name:'Income Statement',type:'is',page:3,confidence:96,rows:18,mapped:16},
  {name:'Balance Sheet',type:'bs',page:5,confidence:94,rows:24,mapped:21},
  {name:'Cash Flow Statement',type:'cf',page:7,confidence:91,rows:15,mapped:12},
  {name:'Fixed Assets Note',type:'notes',page:12,confidence:88,rows:8,mapped:6},
  {name:'Borrowings Note',type:'notes',page:14,confidence:85,rows:6,mapped:4},
]

type Conf = 'high' | 'medium' | 'low' | '';
const modelRows: {label:string,value:string,expr:string,conf:Conf,level:number,isCat?:boolean,isTotal?:boolean}[] = [
  {label:'INCOME STATEMENT',value:'',expr:'',conf:'',level:0,isCat:true},
  {label:'Revenue',value:'12,456,789',expr:'=P3.R4.C3',conf:'high',level:1},
  {label:'Cost of Sales',value:'(8,234,100)',expr:'=NEG(P3.R5.C3)',conf:'high',level:1},
  {label:'Gross Profit',value:'4,222,689',expr:'=SUM(Revenue,CoS)',conf:'high',level:1,isTotal:true},
  {label:'Distribution Costs',value:'(456,200)',expr:'=NEG(P3.R8.C3)',conf:'high',level:1},
  {label:'Administrative Expenses',value:'(1,123,400)',expr:'=NEG(P3.R9.C3)',conf:'medium',level:1},
  {label:'Other Operating Income',value:'89,500',expr:'=P3.R10.C3',conf:'high',level:1},
  {label:'Other Operating Expenses',value:'(34,200)',expr:'',conf:'low',level:1},
  {label:'Operating Profit (EBIT)',value:'2,698,389',expr:'=SUM(GP..OE)',conf:'high',level:1,isTotal:true},
  {label:'Finance Income',value:'123,400',expr:'=P3.R14.C3',conf:'high',level:1},
  {label:'Finance Costs',value:'(567,800)',expr:'=NEG(P3.R15.C3)',conf:'high',level:1},
  {label:'Net Finance Costs',value:'(444,400)',expr:'=SUM(FI,FC)',conf:'high',level:1,isTotal:true},
  {label:'Profit Before Tax',value:'2,253,989',expr:'=SUM(EBIT,NFC)',conf:'high',level:1,isTotal:true},
  {label:'Income Tax Expense',value:'(338,098)',expr:'=NEG(P3.R19.C3)',conf:'medium',level:1},
  {label:'Profit After Tax',value:'1,915,891',expr:'=SUM(PBT,Tax)',conf:'high',level:1,isTotal:true},
  {label:'',value:'',expr:'',conf:'',level:0},
  {label:'BALANCE SHEET — ASSETS',value:'',expr:'',conf:'',level:0,isCat:true},
  {label:'Cash & Cash Equivalents',value:'2,345,600',expr:'=P5.R4.C3',conf:'high',level:1},
  {label:'Trade Receivables',value:'1,890,300',expr:'=P5.R5.C3',conf:'high',level:1},
  {label:'Inventories',value:'876,400',expr:'=P5.R6.C3',conf:'high',level:1},
  {label:'Prepayments',value:'234,100',expr:'=P5.R7.C3',conf:'medium',level:1},
  {label:'Other Current Assets',value:'',expr:'',conf:'low',level:1},
  {label:'Total Current Assets',value:'5,346,400',expr:'=SUM(CA)',conf:'high',level:1,isTotal:true},
  {label:'Property, Plant & Equipment',value:'8,456,700',expr:'=P5.R11.C3+P12.R3.C3',conf:'high',level:1},
  {label:'Intangible Assets',value:'1,234,500',expr:'=P5.R12.C3',conf:'high',level:1},
  {label:'Right-of-Use Assets',value:'567,800',expr:'=P5.R13.C3',conf:'medium',level:1},
  {label:'Total Non-Current Assets',value:'10,259,000',expr:'=SUM(NCA)',conf:'high',level:1,isTotal:true},
  {label:'TOTAL ASSETS',value:'15,605,400',expr:'=SUM(TCA,TNCA)',conf:'high',level:0,isTotal:true},
]

export default function SpreadingWorkspace() {
  const nav = useNavigate()
  const [showZones, setShowZones] = useState(true)
  const [showValidation, setShowValidation] = useState(false)

  const mapped = modelRows.filter(r=>r.conf==='high').length
  const flagged = modelRows.filter(r=>r.conf==='medium').length
  const unresolved = modelRows.filter(r=>r.conf==='low').length
  const total = modelRows.filter(r=>r.conf).length

  return (
    <div style={{height:'100vh',display:'flex',flexDirection:'column',background:'var(--bg-primary)'}}>
      {/* Top toolbar */}
      <div className="workspace-toolbar" style={{borderBottom:'1px solid var(--border-subtle)'}}>
        <button className="btn btn-ghost btn-sm" onClick={()=>nav(-1)}><ArrowLeft size={16}/></button>
        <div style={{display:'flex',alignItems:'center',gap:8,marginLeft:4}}>
          <div style={{width:28,height:28,background:'linear-gradient(135deg,var(--accent),var(--purple))',borderRadius:7,display:'flex',alignItems:'center',justifyContent:'center',fontWeight:800,fontSize:13,color:'white'}}>N</div>
          <span style={{fontWeight:700,fontSize:14}}>Numera</span>
        </div>
        <div className="divider"/>
        <div style={{fontSize:13}}>
          <span style={{fontWeight:600}}>Emirates NBD PJSC</span>
          <span style={{color:'var(--text-muted)',margin:'0 8px'}}>|</span>
          <span style={{color:'var(--text-secondary)'}}>31 Dec 2025 — Annual — Audited — AED</span>
        </div>
        <div style={{flex:1}}/>
        <div style={{display:'flex',alignItems:'center',gap:6}}>
          <Lock size={14} style={{color:'var(--warning)'}}/>
          <span style={{fontSize:11,color:'var(--warning)'}}>Locked by you</span>
        </div>
        <div className="divider"/>

        {/* Mode buttons */}
        <button className="btn btn-ghost btn-sm" style={{background:'rgba(59,130,246,0.1)',color:'var(--accent)',border:'1px solid rgba(59,130,246,0.2)'}}><MousePointer size={14}/>Map Mode</button>
        <button className="btn btn-ghost btn-sm"><Zap size={14}/>Autofill ▾</button>
        <div className="divider"/>
        <button className="btn btn-ghost btn-sm"><History size={14}/></button>
        <button className="btn btn-ghost btn-sm"><MessageSquare size={14}/></button>
        <button className="btn btn-ghost btn-sm"><Settings size={14}/></button>
        <div className="divider"/>
        <button className="btn btn-secondary btn-sm"><Save size={14}/>Save Draft</button>
        <button className="btn btn-primary btn-sm" onClick={()=>setShowValidation(!showValidation)}><Send size={14}/>Submit Spread</button>
      </div>

      {/* Mapping progress bar */}
      <div style={{height:32,background:'var(--bg-card)',borderBottom:'1px solid var(--border-subtle)',display:'flex',alignItems:'center',padding:'0 16px',gap:16,fontSize:12,flexShrink:0}}>
        <span style={{color:'var(--text-muted)'}}>Mapping Progress:</span>
        <div className="progress-bar" style={{width:200}}>
          <div className="fill accent" style={{width:`${(mapped/total)*100}%`}}/>
        </div>
        <span style={{fontWeight:600}}>{Math.round((mapped/total)*100)}%</span>
        <div className="divider" style={{height:16}}/>
        <span style={{color:'var(--success)'}}><CheckCircle2 size={13} style={{display:'inline',verticalAlign:-2}}/> {mapped} auto-accepted</span>
        <span style={{color:'var(--warning)'}}><AlertTriangle size={13} style={{display:'inline',verticalAlign:-2}}/> {flagged} flagged</span>
        <span style={{color:'var(--danger)'}}><XCircle size={13} style={{display:'inline',verticalAlign:-2}}/> {unresolved} unresolved</span>
        <div style={{flex:1}}/>
        <button className="btn btn-success btn-sm" style={{fontSize:11}}>✓ Accept All High Confidence</button>
      </div>

      {/* Main workspace area */}
      <div className="workspace-layout" style={{flex:1}}>
        {/* LEFT PANE — Document Viewer */}
        <div className="workspace-left">
          <div className="workspace-panel-header" style={{justifyContent:'space-between'}}>
            <div style={{display:'flex',alignItems:'center',gap:8}}>
              <FileText size={14}/>
              <span>Emirates_NBD_AR_2025.pdf</span>
              <span style={{color:'var(--text-muted)'}}>— Page 3 of 42</span>
            </div>
            <div style={{display:'flex',gap:4}}>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><ZoomOut size={13}/></button>
              <span style={{fontSize:11,color:'var(--text-muted)',lineHeight:'24px'}}>100%</span>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><ZoomIn size={13}/></button>
              <div className="divider" style={{height:16}}/>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><RotateCw size={13}/></button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><Columns size={13}/></button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><Scissors size={13}/></button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><Merge size={13}/></button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}}><Download size={13}/></button>
            </div>
          </div>
          <div className="workspace-panel-content">
            <div className="pdf-viewer">
              <div className="pdf-page">
                {/* Simulate a financial statement page */}
                <div style={{textAlign:'center',marginBottom:24}}>
                  <div style={{fontSize:16,fontWeight:700,color:'#1a365d'}}>Emirates NBD PJSC</div>
                  <div style={{fontSize:12,color:'#4a6fa5',marginTop:4}}>CONSOLIDATED INCOME STATEMENT</div>
                  <div style={{fontSize:10,color:'#718096',marginTop:2}}>For the year ended 31 December 2025</div>
                </div>
                <table style={{width:'100%',borderCollapse:'collapse',fontSize:11}}>
                  <thead>
                    <tr style={{borderBottom:'2px solid #2d3748'}}>
                      <th style={{textAlign:'left',padding:'6px 4px',fontWeight:600,color:'#2d3748',width:'55%'}}>Notes</th>
                      <th style={{textAlign:'right',padding:'6px 4px',fontWeight:600,color:'#2d3748'}}>2025<br/><span style={{fontWeight:400,fontSize:9}}>AED '000</span></th>
                      <th style={{textAlign:'right',padding:'6px 4px',fontWeight:600,color:'#718096'}}>2024<br/><span style={{fontWeight:400,fontSize:9}}>AED '000</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      ['Revenue from contracts','12,456,789','11,234,567',true],
                      ['Cost of sales','(8,234,100)','(7,456,300)',true],
                      ['Gross profit','4,222,689','3,778,267',false,true],
                      ['','',''],
                      ['Distribution costs','(456,200)','(412,100)',true],
                      ['Administrative expenses','(1,123,400)','(998,700)',true],
                      ['Other operating income','89,500','76,300',true],
                      ['Other operating expenses','(34,200)','(28,900)',true],
                      ['Operating profit','2,698,389','2,414,867',false,true],
                      ['','',''],
                      ['Finance income','123,400','98,700',true],
                      ['Finance costs','(567,800)','(512,300)',true],
                      ['Net finance costs','(444,400)','(413,600)',false,true],
                      ['','',''],
                      ['Profit before tax','2,253,989','2,001,267',false,true],
                      ['Income tax expense','(338,098)','(300,190)',true],
                      ['Profit for the year','1,915,891','1,701,077',false,true],
                    ].map((r,i) => (
                      <tr key={i} style={{borderBottom: r[0] ? '1px solid #e2e8f0' : 'none'}}>
                        <td style={{padding:'5px 4px',fontWeight:r[4]?700:400,color: r[3] ? '#2563eb' : '#2d3748'}}>{r[0]}</td>
                        <td style={{padding:'5px 4px',textAlign:'right',fontWeight:r[4]?700:400,color: r[3] ? '#2563eb' : '#2d3748', background: r[3] ? 'rgba(37,99,235,0.05)' : 'transparent'}}>{r[1]}</td>
                        <td style={{padding:'5px 4px',textAlign:'right',color:'#718096'}}>{r[2]}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {/* Zone overlay */}
                <div className="pdf-zone-overlay is" style={{top:42,left:20,right:20,bottom:20}}>
                  <span className="pdf-zone-label is">Income Statement (96%)</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Resize handle */}
        <div className="resize-handle"><div/></div>

        {/* RIGHT PANE — Model Grid */}
        <div className="workspace-right">
          <div className="workspace-panel-header" style={{justifyContent:'space-between'}}>
            <div style={{display:'flex',alignItems:'center',gap:8}}>
              <Layers size={14}/>
              <span>IFRS Model — CBD MMAS</span>
              <ChevronDown size={14} style={{color:'var(--text-muted)'}}/>
            </div>
            <div style={{display:'flex',gap:4}}>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px',fontSize:11}}>Show/Hide</button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px',fontSize:11}}>Variance</button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px',fontSize:11}}>Currency</button>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px',fontSize:11}}>Unit Scale ▾</button>
            </div>
          </div>

          {/* Category bubbles */}
          <div style={{display:'flex',gap:6,padding:'6px 12px',borderBottom:'1px solid var(--border-subtle)',background:'var(--bg-card)',flexShrink:0,overflowX:'auto'}}>
            {['Income Statement','Balance Sheet — Assets','Balance Sheet — Liabilities','Cash Flow','Ratios'].map((c,i) => (
              <button key={i} className="btn btn-ghost btn-sm" style={{whiteSpace:'nowrap',fontSize:11,padding:'3px 10px',borderRadius:20,background: i===0 ? 'rgba(59,130,246,0.12)' : 'transparent',color: i===0 ? 'var(--accent)' : 'var(--text-muted)',border: i===0 ? '1px solid rgba(59,130,246,0.2)' : '1px solid transparent'}}>{c}</button>
            ))}
          </div>

          <div className="workspace-panel-content">
            <div className="model-grid">
              <div className="grid-row header">
                <div className="cell">Line Item</div>
                <div className="cell" style={{textAlign:'right'}}>Value</div>
                <div className="cell">Expression</div>
                <div className="cell" style={{textAlign:'center'}}>Conf.</div>
                <div className="cell" style={{textAlign:'center'}}>Src</div>
              </div>
              {modelRows.map((r,i) => {
                if(r.isCat) return <div key={i} className="grid-row"><div className="cell category">{r.label}</div></div>
                if(!r.label) return <div key={i} style={{height:8}}/>
                return (
                  <div key={i} className="grid-row" style={{cursor:'pointer'}}>
                    <div className={`cell ${r.level===1?'sub':''} ${r.isTotal?'total':''}`}>{r.label}</div>
                    <div className={`cell value ${r.isTotal?'total':''} mapped-${r.conf}`} style={{fontWeight:r.isTotal?700:400}}>
                      {r.value || <span style={{color:'var(--text-muted)',fontStyle:'italic'}}>unmapped</span>}
                    </div>
                    <div className="cell" style={{fontFamily:'monospace',fontSize:11,color:'var(--cyan)'}}>{r.expr}</div>
                    <div className="cell" style={{textAlign:'center'}}>
                      {r.conf && <span className={`confidence ${r.conf}`}>{r.conf==='high'?'94%':r.conf==='medium'?'76%':'42%'}</span>}
                    </div>
                    <div className="cell" style={{textAlign:'center',fontSize:11,color:'var(--text-muted)'}}>
                      {r.conf ? 'P3' : ''}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>

        {/* Zone panel */}
        {showZones && (
          <div className="zone-panel">
            <div style={{padding:'10px 14px',borderBottom:'1px solid var(--border-subtle)',display:'flex',alignItems:'center',justifyContent:'space-between'}}>
              <span style={{fontSize:12,fontWeight:600}}>Detected Zones</span>
              <button className="btn btn-ghost btn-sm" style={{padding:'2px 6px'}} onClick={()=>setShowZones(false)}><X size={14}/></button>
            </div>
            <div style={{padding:'8px 14px',borderBottom:'1px solid var(--border-subtle)'}}>
              <div style={{fontSize:10,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:6}}>Main Tables</div>
            </div>
            {zones.filter(z=>z.type!=='notes').map((z,i) => (
              <div key={i} className="zone-item">
                <div className="zone-name"><div className={`zone-dot ${z.type}`}/>{z.name}</div>
                <div className="zone-meta">Page {z.page} · {z.mapped}/{z.rows} mapped</div>
                <div style={{marginTop:4}}>
                  <div className="progress-bar" style={{height:3}}>
                    <div className="fill accent" style={{width:`${(z.mapped/z.rows)*100}%`}}/>
                  </div>
                </div>
                <div style={{marginTop:3}}><span className={`confidence ${z.confidence>=90?'high':'medium'}`}>{z.confidence}%</span></div>
              </div>
            ))}
            <div style={{padding:'8px 14px',borderBottom:'1px solid var(--border-subtle)'}}>
              <div style={{fontSize:10,fontWeight:600,color:'var(--text-muted)',textTransform:'uppercase',letterSpacing:1,marginBottom:6}}>Notes / Adjustments</div>
            </div>
            {zones.filter(z=>z.type==='notes').map((z,i) => (
              <div key={i} className="zone-item">
                <div className="zone-name"><div className={`zone-dot ${z.type}`}/>{z.name}</div>
                <div className="zone-meta">Page {z.page} · {z.mapped}/{z.rows} mapped</div>
                <div style={{marginTop:3}}><span className={`confidence ${z.confidence>=90?'high':'medium'}`}>{z.confidence}%</span></div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Validation Modal */}
      {showValidation && (
        <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:100}}>
          <div className="card" style={{width:520,maxHeight:'80vh',overflow:'auto'}}>
            <div className="card-header">
              <div className="card-title">Validation Results</div>
              <button className="btn btn-ghost btn-sm" onClick={()=>setShowValidation(false)}><X size={16}/></button>
            </div>
            <div className="validation-item pass">
              <span className="validation-icon"><Check size={18}/></span>
              <span style={{flex:1}}>Balance Sheet Balance (Assets = L + E)</span>
              <span style={{color:'var(--success)',fontWeight:600,fontSize:12}}>Passed — Diff: 0</span>
            </div>
            <div className="validation-item pass">
              <span className="validation-icon"><Check size={18}/></span>
              <span style={{flex:1}}>Cash Flow Reconciliation</span>
              <span style={{color:'var(--success)',fontWeight:600,fontSize:12}}>Passed — Diff: 0</span>
            </div>
            <div className="validation-item pass">
              <span className="validation-icon"><Check size={18}/></span>
              <span style={{flex:1}}>Gross Profit Calculation</span>
              <span style={{color:'var(--success)',fontWeight:600,fontSize:12}}>Passed — Diff: 0</span>
            </div>
            <div className="validation-item fail">
              <span className="validation-icon"><X size={18}/></span>
              <span style={{flex:1}}>Retained Earnings Reconciliation</span>
              <span style={{color:'var(--danger)',fontWeight:600,fontSize:12}}>Failed — Diff: 12,400</span>
            </div>
            <div style={{marginTop:16}}>
              <div className="input-group">
                <label>Submission Comments</label>
                <textarea className="input" rows={3} placeholder="Add comments for this submission..." style={{resize:'vertical'}}/>
              </div>
              <div style={{display:'flex',gap:8,marginTop:12,justifyContent:'flex-end'}}>
                <button className="btn btn-secondary" onClick={()=>setShowValidation(false)}>Cancel</button>
                <button className="btn btn-primary"><Send size={14}/>Submit Spread</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
