import { useState } from 'react'
import { Upload, Search, Download, Trash2, Lock, FileText, Eye, Link2 } from 'lucide-react'

const files = [
  {id:1,name:'Emirates_NBD_AR_2025.pdf',size:'12.4 MB',lang:'English',status:'ready',uploaded:'2026-04-08',by:'John Doe',mapped:'Emirates NBD'},
  {id:2,name:'HSBC_Holdings_Interim_2025.pdf',size:'8.2 MB',lang:'English',status:'ready',uploaded:'2026-04-07',by:'John Doe',mapped:null},
  {id:3,name:'Unilever_Annual_Report_2025.pdf',size:'15.7 MB',lang:'English',status:'processing',uploaded:'2026-04-09',by:'John Doe',mapped:null},
  {id:4,name:'DP_World_FY2025.pdf',size:'9.1 MB',lang:'English',status:'ready',uploaded:'2026-04-06',by:'Sarah K.',mapped:'DP World Ltd'},
  {id:5,name:'ADNOC_Q3_2025.pdf',size:'4.5 MB',lang:'English',status:'ready',uploaded:'2026-04-05',by:'John Doe',mapped:null},
  {id:6,name:'ENBD_Notes_Dec2025.pdf',size:'3.2 MB',lang:'Arabic',status:'ready',uploaded:'2026-04-04',by:'Ahmed R.',mapped:null},
  {id:7,name:'corrupted_scan.pdf',size:'1.1 MB',lang:'English',status:'error',uploaded:'2026-04-03',by:'John Doe',mapped:null},
  {id:8,name:'BP_PLC_2025_AR.pdf',size:'22.1 MB',lang:'English',status:'ready',uploaded:'2026-04-02',by:'Sarah K.',mapped:'BP PLC'},
]

export default function FileStore() {
  const [tab, setTab] = useState<'my'|'all'|'error'>('my')
  const filtered = tab === 'error' ? files.filter(f=>f.status==='error') : tab === 'my' ? files.filter(f=>f.by==='John Doe') : files

  return (
    <>
      <div className="page-header">
        <h1>File Store</h1>
        <p>Upload and manage financial documents for pre-processing</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="tabs" style={{marginBottom:0,borderBottom:'none'}}>
            <div className={`tab ${tab==='my'?'active':''}`} onClick={()=>setTab('my')}>My Files<span className="tab-count">{files.filter(f=>f.by==='John Doe').length}</span></div>
            <div className={`tab ${tab==='all'?'active':''}`} onClick={()=>setTab('all')}>All Files<span className="tab-count">{files.length}</span></div>
            <div className={`tab ${tab==='error'?'active':''}`} onClick={()=>setTab('error')}>Error Files<span className="tab-count" style={{background:'rgba(239,68,68,0.15)',color:'#f87171'}}>{files.filter(f=>f.status==='error').length}</span></div>
          </div>
        </div>
        <div className="toolbar-right">
          <div className="search-bar"><Search size={16}/><input placeholder="Search files..."/></div>
          <button className="btn btn-primary"><Upload size={16}/>Upload Files</button>
          <button className="btn btn-secondary"><Link2 size={16}/>Map to Customer</button>
        </div>
      </div>

      {/* Upload zone */}
      <div className="card" style={{marginBottom:20,border:'2px dashed var(--border)',background:'transparent',textAlign:'center',padding:32,cursor:'pointer'}}>
        <Upload size={32} style={{color:'var(--text-muted)',marginBottom:8}}/>
        <div style={{fontSize:14,fontWeight:500,marginBottom:4}}>Drop files here or click to upload</div>
        <div style={{fontSize:12,color:'var(--text-muted)'}}>Supports PDF, DOCX, XLSX, JPG, PNG, TIFF — Max 50MB per file</div>
        <div style={{display:'flex',gap:12,justifyContent:'center',marginTop:16}}>
          <div className="input-group" style={{width:180}}>
            <select className="input">
              <option>English</option>
              <option>Arabic</option>
              <option>French</option>
            </select>
          </div>
        </div>
      </div>

      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead>
            <tr>
              <th style={{width:28}}><input type="checkbox"/></th>
              <th>File Name</th>
              <th>Size</th>
              <th>Language</th>
              <th>Status</th>
              <th>Uploaded</th>
              <th>By</th>
              <th>Mapped To</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(f => (
              <tr key={f.id}>
                <td><input type="checkbox"/></td>
                <td>
                  <div style={{display:'flex',alignItems:'center',gap:8}}>
                    <FileText size={16} style={{color:'var(--accent)',flexShrink:0}}/>
                    <span style={{fontWeight:500}}>{f.name}</span>
                    {f.name.includes('Notes') && <Lock size={12} style={{color:'var(--warning)'}}/>}
                  </div>
                </td>
                <td style={{color:'var(--text-secondary)',fontSize:12}}>{f.size}</td>
                <td style={{fontSize:12}}>{f.lang}</td>
                <td><span className={`badge-status ${f.status}`}><span className="dot"/>{f.status}</span></td>
                <td style={{color:'var(--text-secondary)',fontSize:12}}>{f.uploaded}</td>
                <td style={{fontSize:12}}>{f.by}</td>
                <td>{f.mapped ? <span style={{fontSize:12,color:'var(--accent)'}}>{f.mapped}</span> : <span style={{fontSize:12,color:'var(--text-muted)'}}>—</span>}</td>
                <td>
                  <div style={{display:'flex',gap:4}}>
                    <button className="btn btn-ghost btn-sm" title="Preview"><Eye size={14}/></button>
                    <button className="btn btn-ghost btn-sm" title="Download"><Download size={14}/></button>
                    <button className="btn btn-ghost btn-sm" title="Delete" style={{color:'var(--danger)'}}><Trash2 size={14}/></button>
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
