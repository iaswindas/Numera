import { useState } from 'react'
import { Search, Plus, Upload, Download, Edit, Trash2, BookOpen, Layers, ListFilter } from 'lucide-react'

const keywords = [
  {id:1,keyword:'Revenue',synonyms:'Turnover | Net Sales | Total Income | Gross Revenue',zones:'Income Statement',lang:'English'},
  {id:2,keyword:'Cost of Sales',synonyms:'Cost of Goods Sold | COGS | Cost of Revenue',zones:'Income Statement',lang:'English'},
  {id:3,keyword:'Trade Receivables',synonyms:'Accounts Receivable | Amount Receivable | Debtors',zones:'Balance Sheet',lang:'English'},
  {id:4,keyword:'Property Plant Equipment',synonyms:'PPE | Fixed Assets | Tangible Assets',zones:'Balance Sheet | Fixed Assets Note',lang:'English'},
  {id:5,keyword:'Cash and Cash Equivalents',synonyms:'Cash | Bank Balances | Cash at Bank',zones:'Balance Sheet | Cash Flow',lang:'English'},
  {id:6,keyword:'Depreciation',synonyms:'Depreciation Expense | Amort. and Depreciation',zones:'Income Statement | Cash Flow',lang:'English'},
  {id:7,keyword:'Long-term Borrowings',synonyms:'Bank Loans | Term Loans | Long-term Debt',zones:'Balance Sheet | Borrowings Note',lang:'English'},
  {id:8,keyword:'Finance Costs',synonyms:'Interest Expense | Borrowing Costs',zones:'Income Statement',lang:'English'},
]
const globalZones = ['Income Statement','Balance Sheet','Cash Flow Statement','Fixed Assets Note','Receivables Note','Borrowings Note','Inventory Note','Share Capital Note','Segment Reporting','Related Parties','Tax Note','Other Notes']
const exclusionCats = ['Prefix','Suffix','Superscript','Subscript','Punctuation','Spaces','Text Removal','Dots','Dates','Numbers','Brackets','Customer-Specific']

export default function AdminTaxonomy() {
  const [tab, setTab] = useState<'taxonomy'|'zones'|'exclusion'>('taxonomy')
  return (
    <>
      <div className="page-header">
        <h1>Taxonomy, Zones & Exclusion Management</h1>
        <p>Configure global taxonomy keywords, zone definitions, and exclusion lists for AI spreading</p>
      </div>
      <div className="tabs">
        <div className={`tab ${tab==='taxonomy'?'active':''}`} onClick={()=>setTab('taxonomy')}><BookOpen size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Global Taxonomy</div>
        <div className={`tab ${tab==='zones'?'active':''}`} onClick={()=>setTab('zones')}><Layers size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Global Zones</div>
        <div className={`tab ${tab==='exclusion'?'active':''}`} onClick={()=>setTab('exclusion')}><ListFilter size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Exclusion Lists</div>
      </div>

      {tab === 'taxonomy' && (
        <>
          <div className="toolbar">
            <div className="toolbar-left">
              <select className="input" style={{width:180}}><option>IFRS Banking Model</option><option>IFRS Corporate</option><option>US GAAP</option></select>
              <select className="input" style={{width:140}}><option>English</option><option>Arabic</option><option>French</option></select>
              <div className="search-bar"><Search size={16}/><input placeholder="Search keywords..."/></div>
            </div>
            <div className="toolbar-right">
              <button className="btn btn-secondary"><Download size={14}/>Download Taxonomy</button>
              <button className="btn btn-secondary"><Upload size={14}/>Upload Excel</button>
              <button className="btn btn-primary"><Plus size={14}/>Add Taxonomy</button>
            </div>
          </div>
          <div className="card" style={{padding:0,overflow:'hidden'}}>
            <table className="data-table">
              <thead><tr><th>Keyword</th><th>Synonyms</th><th>Global Zones</th><th>Language</th><th>Actions</th></tr></thead>
              <tbody>
                {keywords.map(k => (
                  <tr key={k.id}>
                    <td style={{fontWeight:600,color:'var(--accent)'}}>{k.keyword}</td>
                    <td><div style={{display:'flex',gap:4,flexWrap:'wrap'}}>{k.synonyms.split(' | ').map((s,i)=><span key={i} style={{fontSize:10,padding:'2px 6px',background:'var(--bg-input)',borderRadius:4,border:'1px solid var(--border-subtle)'}}>{s}</span>)}</div></td>
                    <td><div style={{display:'flex',gap:4,flexWrap:'wrap'}}>{k.zones.split(' | ').map((z,i)=><span key={i} style={{fontSize:10,padding:'2px 6px',background:'rgba(59,130,246,0.1)',borderRadius:4,color:'var(--accent)'}}>{z}</span>)}</div></td>
                    <td style={{fontSize:12}}>{k.lang}</td>
                    <td><div style={{display:'flex',gap:4}}><button className="btn btn-ghost btn-sm"><Edit size={14}/></button><button className="btn btn-ghost btn-sm" style={{color:'var(--danger)'}}><Trash2 size={14}/></button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'zones' && (
        <>
          <div className="toolbar">
            <div className="toolbar-left"><div className="search-bar"><Search size={16}/><input placeholder="Search zones..."/></div></div>
            <div className="toolbar-right"><button className="btn btn-primary"><Plus size={14}/>Add Zone</button></div>
          </div>
          <div className="grid-3">
            {globalZones.map((z,i) => (
              <div key={i} className="card" style={{padding:14,display:'flex',alignItems:'center',gap:10}}>
                <div className={`zone-dot ${i<3?['is','bs','cf'][i]:'notes'}`}/>
                <span style={{flex:1,fontSize:13,fontWeight:500}}>{z}</span>
                <span className="badge-status approved" style={{fontSize:10}}><span className="dot"/>Active</span>
                <button className="btn btn-ghost btn-sm"><Edit size={13}/></button>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'exclusion' && (
        <>
          <div style={{fontSize:13,color:'var(--text-secondary)',marginBottom:16,padding:'12px 16px',background:'var(--bg-card)',borderRadius:8,border:'1px solid var(--border-subtle)'}}>
            The Exclusion List filters out irrelevant terms (prefixes, suffixes, noise) from financial line items before taxonomy matching. <strong>Example:</strong> "Note 5: Revenue from operations (continued)" → cleaned to "Revenue from operations"
          </div>
          <div className="grid-3">
            {exclusionCats.map((c,i) => (
              <div key={i} className="card" style={{padding:14}}>
                <div style={{display:'flex',alignItems:'center',justifyContent:'space-between',marginBottom:8}}>
                  <span style={{fontSize:13,fontWeight:600}}>{c}</span>
                  <button className="btn btn-ghost btn-sm"><Edit size={13}/>Modify</button>
                </div>
                <div style={{fontSize:11,color:'var(--text-muted)'}}>
                  {i===0 ? 'a), b), c), d), i), ii), Note' :
                   i===1 ? '***, Note 1, $mil, (continued)' :
                   i===4 ? '! , . ; : @ # $ % &' :
                   i===6 ? 'pursuant to, as disclosed in' :
                   `${3+i} items configured`}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </>
  )
}
