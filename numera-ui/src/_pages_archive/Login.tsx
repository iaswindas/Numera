import { useNavigate } from 'react-router-dom'
import { Lock, Globe } from 'lucide-react'

export default function Login() {
  const nav = useNavigate()
  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <div style={{display:'flex',alignItems:'center',justifyContent:'center',gap:12,marginBottom:8}}>
            <div style={{width:48,height:48,background:'linear-gradient(135deg,var(--accent),var(--purple))',borderRadius:14,display:'flex',alignItems:'center',justifyContent:'center',fontWeight:800,fontSize:24,color:'white'}}>N</div>
          </div>
          <h1>Numera</h1>
          <p>AI-First Financial Spreading Platform</p>
        </div>
        <div className="login-form">
          <button className="btn btn-secondary btn-lg" style={{width:'100%',justifyContent:'center',padding:'12px 20px'}} onClick={()=>nav('/dashboard')}>
            <Globe size={18} />
            Sign in with SSO
          </button>
          <div className="login-divider">or continue with credentials</div>
          <div className="input-group">
            <label>Email Address</label>
            <input className="input" type="email" placeholder="analyst@bank.com" />
          </div>
          <div className="input-group">
            <label>Password</label>
            <input className="input" type="password" placeholder="••••••••" />
          </div>
          <div style={{display:'flex',alignItems:'center',justifyContent:'space-between',fontSize:13}}>
            <label style={{display:'flex',alignItems:'center',gap:6,color:'var(--text-secondary)',cursor:'pointer'}}>
              <input type="checkbox" /> Remember me
            </label>
            <a href="#" style={{color:'var(--accent)',textDecoration:'none'}}>Forgot password?</a>
          </div>
          <button className="btn btn-primary btn-lg" style={{width:'100%',justifyContent:'center',padding:'12px 20px'}} onClick={()=>nav('/dashboard')}>
            <Lock size={16} />
            Sign In
          </button>
          <div style={{textAlign:'center',fontSize:12,color:'var(--text-muted)',marginTop:8}}>
            Protected by Multi-Factor Authentication
          </div>
        </div>
      </div>
    </div>
  )
}
