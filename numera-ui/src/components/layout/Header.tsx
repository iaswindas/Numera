'use client'
import { usePathname } from 'next/navigation'
import { ChevronRight, Search, Bell } from 'lucide-react'

const breadcrumbMap: Record<string, string[]> = {
  '/dashboard': ['Dashboard'],
  '/documents': ['Spreading', 'File Store'],
  '/customers': ['Spreading', 'Search Customer'],
  '/covenants/management': ['Covenants', 'Covenant Management'],
  '/covenants': ['Covenants', 'Covenant Items'],
  '/covenant-intelligence': ['Covenants', 'Intelligence'],
  '/admin/formulas': ['Covenants', 'Formula Management'],
  '/email-templates': ['Covenants', 'Email Templates'],
  '/reports': ['Analytics', 'Reports & MIS'],
  '/admin/users': ['Admin', 'User Management'],
  '/admin/taxonomy': ['Admin', 'Taxonomy & Zones'],
  '/admin/workflows': ['Admin', 'Workflow Designer'],
  '/settings': ['Settings'],
}

function getBreadcrumb(pathname: string): string[] {
  if (breadcrumbMap[pathname]) return breadcrumbMap[pathname]
  if (pathname.includes('/items')) return ['Spreading', 'Customer', 'Spread Items']
  if (pathname.startsWith('/customers/')) return ['Spreading', 'Customer']
  if (pathname.startsWith('/spreading/')) return ['Spreading', 'Workspace']
  if (pathname.startsWith('/covenants/')) return ['Covenants', 'Items']
  return ['Dashboard']
}

export function Header() {
  const pathname = usePathname()
  const crumbs = getBreadcrumb(pathname ?? '/')

  return (
    <header className="top-bar">
      <div className="top-bar-breadcrumb">
        {crumbs.map((c, i) => (
          <span key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {i > 0 && <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />}
            <span className={i === crumbs.length - 1 ? 'current' : ''}>
              {c}
            </span>
          </span>
        ))}
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
  )
}
