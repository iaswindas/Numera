import { Outlet, NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, Upload, Search, FileSpreadsheet, Shield, BarChart3,
  Users, BookOpen, Settings, Bell, ChevronRight, Workflow, Mail,
  Calculator, Activity
} from 'lucide-react'

const nav = [
  { section: 'Main', items: [
    { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  ]},
  { section: 'Spreading', items: [
    { to: '/customers', icon: Search, label: 'Search Customer' },
    { to: '/file-store', icon: Upload, label: 'File Store' },
  ]},
  { section: 'Covenants', items: [
    { to: '/covenant-management', icon: Shield, label: 'Covenant Mgmt', badge: 3 },
    { to: '/covenant-items', icon: FileSpreadsheet, label: 'Covenant Items' },
    { to: '/covenant-dashboard', icon: Activity, label: 'Intelligence' },
    { to: '/formulas', icon: Calculator, label: 'Formula Mgmt' },
    { to: '/email-templates', icon: Mail, label: 'Email Templates' },
  ]},
  { section: 'Analytics', items: [
    { to: '/reports', icon: BarChart3, label: 'Reports & MIS' },
  ]},
  { section: 'Admin', items: [
    { to: '/admin/users', icon: Users, label: 'User Management' },
    { to: '/admin/taxonomy', icon: BookOpen, label: 'Taxonomy & Zones' },
    { to: '/admin/workflows', icon: Workflow, label: 'Workflow Designer' },
  ]},
]

function getBreadcrumb(pathname: string) {
  const m: Record<string, string[]> = {
    '/dashboard': ['Dashboard'],
    '/file-store': ['Spreading', 'File Store'],
    '/customers': ['Spreading', 'Search Customer'],
    '/covenant-management': ['Covenants', 'Covenant Management'],
    '/covenant-items': ['Covenants', 'Covenant Items'],
    '/covenant-dashboard': ['Covenants', 'Intelligence'],
    '/formulas': ['Covenants', 'Formula Management'],
    '/email-templates': ['Covenants', 'Email Templates'],
    '/reports': ['Analytics', 'Reports & MIS'],
    '/admin/users': ['Admin', 'User Management'],
    '/admin/taxonomy': ['Admin', 'Taxonomy & Zones'],
    '/admin/workflows': ['Admin', 'Workflow Designer'],
  }
  if (pathname.includes('/items')) return ['Spreading', 'Customer', 'Existing Items']
  return m[pathname] || ['Dashboard']
}

export default function Layout() {
  const loc = useLocation()
  const crumbs = getBreadcrumb(loc.pathname)

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="logo-icon">N</div>
          <div className="logo-text">Numera</div>
        </div>
        {nav.map(s => (
          <div className="sidebar-section" key={s.section}>
            <div className="sidebar-section-title">{s.section}</div>
            {s.items.map(it => (
              <NavLink key={it.to} to={it.to} className={({isActive}) => `sidebar-item ${isActive ? 'active' : ''}`}>
                <it.icon />
                <span>{it.label}</span>
                {it.badge && <span className="badge">{it.badge}</span>}
              </NavLink>
            ))}
          </div>
        ))}
        <div style={{flex: 1}} />
        <div style={{padding: '16px 12px', borderTop: '1px solid var(--border-subtle)'}}>
          <div className="sidebar-item" style={{cursor: 'pointer'}}>
            <Settings size={18} />
            <span>Settings</span>
          </div>
          <div style={{display: 'flex', alignItems: 'center', gap: 10, padding: '12px 12px 4px'}}>
            <div className="avatar">JD</div>
            <div>
              <div style={{fontSize: 13, fontWeight: 600}}>John Doe</div>
              <div style={{fontSize: 11, color: 'var(--text-muted)'}}>Analyst</div>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-content">
        <header className="top-bar">
          <div className="top-bar-left">
            <div className="top-bar-breadcrumb">
              {crumbs.map((c, i) => (
                <span key={i}>
                  {i > 0 && <span className="sep"><ChevronRight size={14} /></span>}
                  <span className={i === crumbs.length - 1 ? 'current' : ''} style={i < crumbs.length - 1 ? {color: 'var(--text-muted)'} : {}}>
                    {c}
                  </span>
                </span>
              ))}
            </div>
          </div>
          <div className="top-bar-right">
            <div className="search-bar">
              <Search size={16} />
              <input placeholder="Search anything..." />
            </div>
            <div className="notification-bell">
              <Bell size={20} />
              <span className="bell-dot" />
            </div>
            <div className="avatar">JD</div>
          </div>
        </header>
        <div className="page-content">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
