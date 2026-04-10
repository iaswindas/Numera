import React from 'react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid, AreaChart, Area } from 'recharts'
import { AlertTriangle, TrendingUp, TrendingDown, Calendar, MessageSquare, Target } from 'lucide-react'

const trendData = [
  {q:'Q1 24',dscr:1.8,debt:3.1,icr:3.2},{q:'Q2 24',dscr:1.7,debt:3.3,icr:3.0},
  {q:'Q3 24',dscr:1.6,debt:3.5,icr:2.8},{q:'Q4 24',dscr:1.5,debt:3.6,icr:2.7},
  {q:'Q1 25',dscr:1.45,debt:3.7,icr:2.6},{q:'Q2 25',dscr:1.42,debt:3.8,icr:2.5},
  {q:'Q3 25',dscr:1.4,debt:3.9,icr:2.4},{q:'Q4 25',dscr:1.38,debt:4.1,icr:2.3},
  {q:'Q1 26*',dscr:1.32,debt:4.3,icr:2.2},{q:'Q2 26*',dscr:1.28,debt:4.5,icr:2.1},
]
const heatmapData = [
  {name:'Emirates NBD',vals:[12,22,8,5,18]},
  {name:'HSBC Holdings',vals:[8,45,15,32,12]},
  {name:'DP World',vals:[92,88,65,42,78]},
  {name:'ADNOC Dist.',vals:[18,65,22,12,35]},
  {name:'Saudi Aramco',vals:[5,8,12,22,15]},
]
const covLabels = ['DSCR','D/EBITDA','Curr. Ratio','ICR','D/Equity']
const topRisks = [
  {customer:'DP World',covenant:'DSCR ≥ 1.5x',prob:92,value:'1.38x',threshold:'1.5x',trend:'down'},
  {customer:'DP World',covenant:'Net Debt/EBITDA ≤ 3.5x',prob:88,value:'3.72x',threshold:'3.5x',trend:'up'},
  {customer:'DP World',covenant:'Current Ratio ≥ 1.2x',prob:78,value:'1.24x',threshold:'1.2x',trend:'down'},
  {customer:'ADNOC Dist.',covenant:'Current Ratio ≥ 1.2x',prob:65,value:'1.28x',threshold:'1.2x',trend:'down'},
  {customer:'HSBC Holdings',covenant:'Tier 1 Capital ≥ 12%',prob:45,value:'—',threshold:'12%',trend:'flat'},
]

function getCellClass(v: number) { return v > 75 ? 'critical' : v > 50 ? 'high' : v > 30 ? 'med' : 'low' }

export default function CovenantDashboard() {
  return (
    <>
      <div className="page-header">
        <h1>Covenant Intelligence</h1>
        <p>Predictive breach analytics and portfolio-level covenant monitoring</p>
      </div>

      <div className="stat-grid" style={{gridTemplateColumns:'repeat(4,1fr)'}}>
        <div className="stat-card danger">
          <div className="stat-label"><AlertTriangle size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>High Risk ({'>'}75%)</div>
          <div className="stat-value">3</div>
          <div className="stat-change down"><TrendingUp size={14}/>+1 from last month</div>
        </div>
        <div className="stat-card warning">
          <div className="stat-label">Medium Risk (40-75%)</div>
          <div className="stat-value">5</div>
        </div>
        <div className="stat-card success">
          <div className="stat-label">Low Risk (&lt;40%)</div>
          <div className="stat-value">42</div>
        </div>
        <div className="stat-card purple">
          <div className="stat-label"><Calendar size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Due This Month</div>
          <div className="stat-value">8</div>
        </div>
      </div>

      {/* Breach Risk Heatmap */}
      <div className="card" style={{marginBottom:24}}>
        <div className="card-header">
          <div>
            <div className="card-title">Breach Risk Heatmap</div>
            <div className="card-subtitle">Breach probability by customer and covenant type — darker = higher risk</div>
          </div>
          <div style={{display:'flex',gap:8,fontSize:11}}>
            <span style={{display:'flex',alignItems:'center',gap:4}}><span style={{width:12,height:12,borderRadius:2,background:'rgba(16,185,129,0.2)'}}/>Low &lt;30%</span>
            <span style={{display:'flex',alignItems:'center',gap:4}}><span style={{width:12,height:12,borderRadius:2,background:'rgba(245,158,11,0.2)'}}/>Med 30-50%</span>
            <span style={{display:'flex',alignItems:'center',gap:4}}><span style={{width:12,height:12,borderRadius:2,background:'rgba(239,68,68,0.2)'}}/>High 50-75%</span>
            <span style={{display:'flex',alignItems:'center',gap:4}}><span style={{width:12,height:12,borderRadius:2,background:'rgba(239,68,68,0.4)'}}/>Critical {'>'}75%</span>
          </div>
        </div>
        <div className="heatmap-grid">
          <div className="heatmap-header"/>
          {covLabels.map((l,i) => <div key={i} className="heatmap-header">{l}</div>)}
          {heatmapData.map((row,i) => (
            <React.Fragment key={`r-${i}`}>
              <div className="heatmap-label" style={{fontWeight:500,fontSize:12}}>{row.name}</div>
              {row.vals.map((v,j) => (
                <div key={`c-${i}-${j}`} className={`heatmap-cell ${getCellClass(v)}`}>{v}%</div>
              ))}
            </React.Fragment>
          ))}
        </div>
      </div>

      <div className="grid-2" style={{marginBottom:24}}>
        {/* Trend chart */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">DP World — DSCR Trend & Forecast</div>
              <div className="card-subtitle">Historical values with AI-projected forecast (*)</div>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={trendData}>
              <CartesianGrid stroke="#1e3a5f" strokeDasharray="3 3" />
              <XAxis dataKey="q" stroke="#64748b" fontSize={11} tickLine={false}/>
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} domain={[1,2]}/>
              <Tooltip contentStyle={{background:'#1a2236',border:'1px solid #1e3a5f',borderRadius:8,fontSize:12}}/>
              <Line type="monotone" dataKey="dscr" stroke="#3b82f6" strokeWidth={2} dot={{r:3,fill:'#3b82f6'}}/>
              {/* Threshold line */}
              <Line type="monotone" dataKey={() => 1.5} stroke="#ef4444" strokeDasharray="6 3" strokeWidth={1} dot={false} name="Threshold"/>
            </LineChart>
          </ResponsiveContainer>
          <div style={{textAlign:'center',fontSize:11,color:'var(--text-muted)',marginTop:8}}>
            <span style={{color:'var(--danger)'}}>— — —</span> Threshold (1.5x) &nbsp;&nbsp;
            <span style={{color:'var(--accent)'}}>——</span> Actual & Forecasted DSCR
          </div>
        </div>

        {/* Top Risks Table */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Highest Risk Covenants</div>
              <div className="card-subtitle">Top 5 covenants by breach probability</div>
            </div>
          </div>
          <div style={{display:'flex',flexDirection:'column',gap:8}}>
            {topRisks.map((r,i) => (
              <div key={i} style={{display:'flex',alignItems:'center',gap:12,padding:'10px 14px',background:'var(--bg-input)',borderRadius:8,borderLeft:`3px solid ${r.prob>75?'var(--danger)':r.prob>40?'var(--warning)':'var(--success)'}`}}>
                <div style={{width:28,textAlign:'center',fontWeight:800,fontSize:14,color:r.prob>75?'var(--danger)':r.prob>40?'var(--warning)':'var(--success)'}}>{i+1}</div>
                <div style={{flex:1}}>
                  <div style={{fontSize:13,fontWeight:600}}>{r.customer}</div>
                  <div style={{fontSize:11,color:'var(--text-muted)'}}>{r.covenant}</div>
                </div>
                <div style={{textAlign:'right',marginRight:8}}>
                  <div style={{fontSize:13,fontWeight:600,fontFamily:'monospace'}}>{r.value}</div>
                  <div style={{fontSize:10,color:'var(--text-muted)'}}>thr: {r.threshold}</div>
                </div>
                <div style={{display:'flex',alignItems:'center',gap:6}}>
                  <div className="progress-bar" style={{width:50,height:5}}>
                    <div className="fill" style={{width:`${r.prob}%`,background:r.prob>75?'var(--danger)':r.prob>40?'var(--warning)':'var(--success)'}}/>
                  </div>
                  <span style={{fontSize:12,fontWeight:700,width:36,textAlign:'right',color:r.prob>75?'var(--danger)':r.prob>40?'var(--warning)':'var(--success)'}}>{r.prob}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* NL Query */}
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title"><MessageSquare size={16} style={{display:'inline',verticalAlign:-3,marginRight:6}}/>Natural Language Query</div>
            <div className="card-subtitle">Ask questions about your covenant portfolio in plain English</div>
          </div>
        </div>
        <div style={{display:'flex',gap:8}}>
          <input className="input" style={{flex:1}} placeholder='Try: "Which covenants are at risk of breaching this month?" or "Show me all clients with DSCR below 1.5x"'/>
          <button className="btn btn-primary">Ask Numera</button>
        </div>
        <div style={{display:'flex',gap:8,marginTop:10,flexWrap:'wrap'}}>
          {['Which covenants breach this quarter?','Show DSCR trends for all clients','Clients with current ratio below 1.2x','Portfolio average Debt/EBITDA'].map((q,i) => (
            <button key={i} className="btn btn-ghost btn-sm" style={{fontSize:11,border:'1px solid var(--border-subtle)',borderRadius:20}}>{q}</button>
          ))}
        </div>
      </div>
    </>
  )
}
