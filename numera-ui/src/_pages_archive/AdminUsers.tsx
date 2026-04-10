import { Search, Plus, Edit, UserCheck, UserX, Upload, Shield } from 'lucide-react'

const users = [
  {id:1,name:'John Doe',email:'john.doe@bank.com',role:'Analyst',group:'UAE Banking',isRM:false,status:'Active',lastLogin:'2026-04-09 14:32'},
  {id:2,name:'Sarah Khan',email:'sarah.k@bank.com',role:'Manager',group:'UAE Banking',isRM:true,status:'Active',lastLogin:'2026-04-09 10:15'},
  {id:3,name:'Ahmed Rashid',email:'ahmed.r@bank.com',role:'Analyst',group:'GCC Corporate',isRM:false,status:'Active',lastLogin:'2026-04-08 16:45'},
  {id:4,name:'Emma Wilson',email:'emma.w@bank.com',role:'Global Manager',group:'Europe Banking',isRM:true,status:'Active',lastLogin:'2026-04-07 09:22'},
  {id:5,name:'Fatima Al-M.',email:'fatima@bank.com',role:'Analyst',group:'UAE Corporate',isRM:false,status:'Pending',lastLogin:'—'},
  {id:6,name:'Tom Richards',email:'tom.r@bank.com',role:'Auditor',group:'Europe Corporate',isRM:false,status:'Active',lastLogin:'2026-04-05 11:30'},
  {id:7,name:'Priya Sharma',email:'priya.s@bank.com',role:'Analyst',group:'UAE Banking',isRM:false,status:'Inactive',lastLogin:'2026-03-01 08:15'},
  {id:8,name:'Michael Chen',email:'michael.c@bank.com',role:'Admin',group:'All',isRM:false,status:'Active',lastLogin:'2026-04-09 08:00'},
]

export default function AdminUsers() {
  return (
    <>
      <div className="page-header">
        <h1>User Management</h1>
        <p>Manage user accounts, roles, and group assignments</p>
      </div>
      <div className="stat-grid" style={{gridTemplateColumns:'repeat(4,1fr)'}}>
        <div className="stat-card accent"><div className="stat-label">Total Users</div><div className="stat-value">{users.length}</div></div>
        <div className="stat-card success"><div className="stat-label">Active</div><div className="stat-value">{users.filter(u=>u.status==='Active').length}</div></div>
        <div className="stat-card warning"><div className="stat-label">Pending Approval</div><div className="stat-value">{users.filter(u=>u.status==='Pending').length}</div></div>
        <div className="stat-card danger"><div className="stat-label">Inactive</div><div className="stat-value">{users.filter(u=>u.status==='Inactive').length}</div></div>
      </div>
      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar"><Search size={16}/><input placeholder="Search users..."/></div>
          <select className="input" style={{width:150}}><option>All Roles</option><option>Admin</option><option>Analyst</option><option>Manager</option><option>Global Manager</option><option>Auditor</option></select>
          <select className="input" style={{width:150}}><option>All Status</option><option>Active</option><option>Pending</option><option>Inactive</option></select>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-secondary"><Upload size={16}/>Bulk Import CSV</button>
          <button className="btn btn-primary"><Plus size={16}/>Add User</button>
        </div>
      </div>
      <div className="card" style={{padding:0,overflow:'hidden'}}>
        <table className="data-table">
          <thead><tr><th>User</th><th>Email</th><th>Role</th><th>Group</th><th>Is RM</th><th>Status</th><th>Last Login</th><th>Actions</th></tr></thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td><div style={{display:'flex',alignItems:'center',gap:8}}>
                  <div className="avatar avatar-sm">{u.name.split(' ').map(n=>n[0]).join('')}</div>
                  <span style={{fontWeight:500}}>{u.name}</span>
                </div></td>
                <td style={{fontSize:12,color:'var(--text-secondary)'}}>{u.email}</td>
                <td><span style={{fontSize:11,padding:'2px 10px',borderRadius:20,background:u.role==='Admin'?'rgba(239,68,68,0.1)':u.role==='Manager'?'rgba(139,92,246,0.1)':u.role==='Global Manager'?'rgba(245,158,11,0.1)':'rgba(59,130,246,0.1)',color:u.role==='Admin'?'#f87171':u.role==='Manager'?'#a78bfa':u.role==='Global Manager'?'#fbbf24':'#60a5fa',fontWeight:600}}>{u.role}</span></td>
                <td style={{fontSize:12}}>{u.group}</td>
                <td>{u.isRM ? <Shield size={14} style={{color:'var(--success)'}}/> : <span style={{color:'var(--text-muted)'}}>—</span>}</td>
                <td><span className={`badge-status ${u.status==='Active'?'approved':u.status==='Pending'?'due':'draft'}`}><span className="dot"/>{u.status}</span></td>
                <td style={{fontSize:12,fontFamily:'monospace',color:'var(--text-muted)'}}>{u.lastLogin}</td>
                <td><div style={{display:'flex',gap:4}}>
                  <button className="btn btn-ghost btn-sm"><Edit size={14}/></button>
                  {u.status==='Pending' && <button className="btn btn-success btn-sm"><UserCheck size={14}/>Approve</button>}
                  {u.status==='Active' && <button className="btn btn-ghost btn-sm" style={{color:'var(--danger)'}}><UserX size={14}/></button>}
                </div></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
