export function LoadingSkeleton({ width = '100%', height = 16 }: { width?: string | number; height?: string | number }) {
  return (
    <div
      style={{
        width,
        height,
        borderRadius: 8,
        background: 'linear-gradient(90deg, rgba(148,163,184,0.15), rgba(148,163,184,0.25), rgba(148,163,184,0.15))',
        backgroundSize: '200% 100%',
        animation: 'pulse 1.4s ease-in-out infinite',
      }}
    />
  )
}
