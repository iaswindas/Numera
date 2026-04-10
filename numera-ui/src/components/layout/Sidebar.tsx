'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  LayoutDashboard, Upload, Search, FileSpreadsheet, Shield, BarChart3,
  Users, BookOpen, Settings, Workflow, Mail, Calculator, Activity,
} from 'lucide-react'

const nav = [
  {
    section: 'Main',
    items: [
      { href: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
    ],
  },
  {
    section: 'Spreading',
    items: [
      { href: '/customers', icon: Search, label: 'Search Customer' },
      { href: '/documents', icon: Upload, label: 'File Store' },
    ],
  },
  {
    section: 'Covenants',
    items: [
      { href: '/covenants/management', icon: Shield, label: 'Covenant Mgmt', badge: 3 },
      { href: '/covenants', icon: FileSpreadsheet, label: 'Covenant Items' },
      { href: '/covenant-intelligence', icon: Activity, label: 'Intelligence' },
      { href: '/admin/formulas', icon: Calculator, label: 'Formula Mgmt' },
      { href: '/email-templates', icon: Mail, label: 'Email Templates' },
    ],
  },
  {
    section: 'Analytics',
    items: [
      { href: '/reports', icon: BarChart3, label: 'Reports & MIS' },
    ],
  },
  {
    section: 'Admin',
    items: [
      { href: '/admin/users', icon: Users, label: 'User Management' },
      { href: '/admin/taxonomy', icon: BookOpen, label: 'Taxonomy & Zones' },
      { href: '/admin/workflows', icon: Workflow, label: 'Workflow Designer' },
    ],
  },
]

export function Sidebar() {
  const pathname = usePathname()

  const isActive = (href: string) => {
    if (href === '/dashboard') return pathname === '/dashboard'
    return pathname?.startsWith(href) ?? false
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">N</div>
        <div className="logo-text">Numera</div>
      </div>

      {nav.map(section => (
        <div className="sidebar-section" key={section.section}>
          <div className="sidebar-section-title">{section.section}</div>
          {section.items.map(item => (
            <Link
              key={item.href}
              href={item.href}
              className={`sidebar-item ${isActive(item.href) ? 'active' : ''}`}
            >
              <item.icon size={18} />
              <span>{item.label}</span>
              {item.badge && <span className="badge">{item.badge}</span>}
            </Link>
          ))}
        </div>
      ))}

      <div style={{ flex: 1 }} />

      <div style={{ padding: '16px 12px', borderTop: '1px solid var(--border-subtle)' }}>
        <Link href="/settings" className="sidebar-item">
          <Settings size={18} />
          <span>Settings</span>
        </Link>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 12px 4px' }}>
          <div className="avatar">JD</div>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>John Doe</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Analyst</div>
          </div>
        </div>
      </div>
    </aside>
  )
}
