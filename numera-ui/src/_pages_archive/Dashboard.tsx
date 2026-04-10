import { useNavigate } from 'react-router-dom'
import { TrendingUp, TrendingDown, FileText, Clock, Zap, Target, ArrowRight, Activity } from 'lucide-react'
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

const spreadTrend = [
  {m:'Jan',v:42},{m:'Feb',v:58},{m:'Mar',v:73},{m:'Apr',v:65},{m:'May',v:89},{m:'Jun',v:95},{m:'Jul',v:112},
]
const accuracyTrend = [
  {m:'Jan',v:82},{m:'Feb',v:85},{m:'Mar',v:87},{m:'Apr',v:89},{m:'May',v:91},{m:'Jun',v:92},{m:'Jul',v:93},
]
const statusPie = [
  {name:'Met',value:45,color:'#10b981'},
  {name:'Due',value:18,color:'#f59e0b'},
  {name:'Overdue',value:8,color:'#ef4444'},
  {name:'Breached',value:5,color:'#dc2626'},
  {name:'Closed',value:24,color:'#8b5cf6'},
]
const recentSpreads = [
  {customer:'Emirates NBD',date:'31 Dec 2025',status:'approved',accuracy:94,time:'2m 14s'},
  {customer:'HSBC Holdings',date:'30 Jun 2025',status:'submitted',accuracy:91,time:'3m 05s'},
  {customer:'Unilever PLC',date:'31 Dec 2025',status:'draft',accuracy:88,time:'—'},
  {customer:'DP World Ltd',date:'30 Sep 2025',status:'approved',accuracy:96,time:'1m 48s'},
  {customer:'ADNOC Chemicals',date:'31 Dec 2025',status:'processing',accuracy:0,time:'—'},
]

export default function Dashboard() {
  const nav = useNavigate()
  return (
    <>
      <div className="page-header">
        <h1>Dashboard</h1>
        <p>Overview of your spreading and covenant activities</p>
      </div>
      <div className="stat-grid">
        <div className="stat-card accent">
          <div className="stat-label"><FileText size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Total Spreads</div>
          <div className="stat-value">534</div>
          <div className="stat-change up"><TrendingUp size={14}/>+12.3% vs last month</div>
        </div>
        <div className="stat-card success">
          <div className="stat-label"><Zap size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>AI Accuracy</div>
          <div className="stat-value">93.2%</div>
          <div className="stat-change up"><TrendingUp size={14}/>+1.8% improving</div>
        </div>
        <div className="stat-card warning">
          <div className="stat-label"><Clock size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Avg Processing Time</div>
          <div className="stat-value">2m 34s</div>
          <div className="stat-change up"><TrendingDown size={14}/>-18s faster</div>
        </div>
        <div className="stat-card danger">
          <div className="stat-label"><Target size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Covenants at Risk</div>
          <div className="stat-value">13</div>
          <div className="stat-change down"><TrendingUp size={14}/>+3 from last week</div>
        </div>
        <div className="stat-card purple">
          <div className="stat-label"><Activity size={14} style={{display:'inline',verticalAlign:-2,marginRight:4}}/>Active Customers</div>
          <div className="stat-value">87</div>
          <div className="stat-change up"><TrendingUp size={14}/>+5 this month</div>
        </div>
      </div>

      <div className="grid-2" style={{marginBottom:24}}>
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Spreads Processed</div>
              <div className="card-subtitle">Monthly trend over last 7 months</div>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={spreadTrend}>
              <defs>
                <linearGradient id="g1" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.3}/>
                  <stop offset="100%" stopColor="#3b82f6" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <XAxis dataKey="m" stroke="#64748b" fontSize={11} tickLine={false} axisLine={false}/>
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false}/>
              <Tooltip contentStyle={{background:'#1a2236',border:'1px solid #1e3a5f',borderRadius:8,fontSize:12}} />
              <Area type="monotone" dataKey="v" stroke="#3b82f6" fill="url(#g1)" strokeWidth={2}/>
            </AreaChart>
          </ResponsiveContainer>
        </div>
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">AI Accuracy Trend</div>
              <div className="card-subtitle">Auto-accepted mapping rate improvement</div>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={accuracyTrend}>
              <defs>
                <linearGradient id="g2" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#10b981" stopOpacity={0.3}/>
                  <stop offset="100%" stopColor="#10b981" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <XAxis dataKey="m" stroke="#64748b" fontSize={11} tickLine={false} axisLine={false}/>
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} domain={[75,100]}/>
              <Tooltip contentStyle={{background:'#1a2236',border:'1px solid #1e3a5f',borderRadius:8,fontSize:12}} />
              <Area type="monotone" dataKey="v" stroke="#10b981" fill="url(#g2)" strokeWidth={2}/>
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header">
            <div className="card-title">Recent Spreads</div>
            <button className="btn btn-ghost btn-sm">View All <ArrowRight size={14}/></button>
          </div>
          <table className="data-table">
            <thead>
              <tr><th>Customer</th><th>Date</th><th>Status</th><th>AI Acc.</th><th>Time</th></tr>
            </thead>
            <tbody>
              {recentSpreads.map((s,i) => (
                <tr key={i} style={{cursor:'pointer'}} onClick={()=>nav('/workspace')}>
                  <td style={{fontWeight:500}}>{s.customer}</td>
                  <td style={{color:'var(--text-secondary)'}}>{s.date}</td>
                  <td><span className={`badge-status ${s.status}`}><span className="dot"/>{s.status}</span></td>
                  <td>{s.accuracy ? <span className={`confidence ${s.accuracy>=90?'high':s.accuracy>=70?'medium':'low'}`}>{s.accuracy}%</span> : '—'}</td>
                  <td style={{color:'var(--text-secondary)',fontFamily:'monospace',fontSize:12}}>{s.time}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="card">
          <div className="card-header">
            <div className="card-title">Covenant Status Distribution</div>
            <button className="btn btn-ghost btn-sm" onClick={()=>nav('/covenant-dashboard')}>View Intelligence <ArrowRight size={14}/></button>
          </div>
          <div style={{display:'flex',alignItems:'center',gap:24}}>
            <ResponsiveContainer width="50%" height={200}>
              <PieChart>
                <Pie data={statusPie} dataKey="value" cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={3}>
                  {statusPie.map((e,i) => <Cell key={i} fill={e.color} />)}
                </Pie>
                <Tooltip contentStyle={{background:'#1a2236',border:'1px solid #1e3a5f',borderRadius:8,fontSize:12}} />
              </PieChart>
            </ResponsiveContainer>
            <div style={{flex:1}}>
              {statusPie.map((s,i) => (
                <div key={i} style={{display:'flex',alignItems:'center',gap:8,marginBottom:8,fontSize:13}}>
                  <div style={{width:10,height:10,borderRadius:3,background:s.color,flexShrink:0}}/>
                  <span style={{flex:1,color:'var(--text-secondary)'}}>{s.name}</span>
                  <span style={{fontWeight:600}}>{s.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
