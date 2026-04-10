import { Sidebar } from '@/components/layout/Sidebar'
import { Header } from '@/components/layout/Header'
import { AuthGuard } from '@/components/providers/AuthGuard'

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="app-layout">
        <Sidebar />
        <div className="main-content">
          <Header />
          <div className="page-content">
            {children}
          </div>
        </div>
      </div>
    </AuthGuard>
  )
}
