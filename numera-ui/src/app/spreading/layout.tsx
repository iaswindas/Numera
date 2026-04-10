// Spreading workspace has its own full-screen layout (no sidebar/header)
export default function SpreadingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ height: '100vh', overflow: 'hidden', background: 'var(--bg-primary)' }}>
      {children}
    </div>
  )
}
