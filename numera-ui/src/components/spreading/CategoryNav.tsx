'use client'

interface CategoryNavProps {
  categories: string[]
  activeCategory: string
  onCategoryClick: (category: string) => void
}

export function CategoryNav({ categories, activeCategory, onCategoryClick }: CategoryNavProps) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '12px 0',
        overflowX: 'auto',
        scrollBehavior: 'smooth',
      }}
    >
      {categories.map((category) => (
        <button
          key={category}
          onClick={() => onCategoryClick(category)}
          style={{
            padding: '6px 14px',
            borderRadius: 20,
            border: 'none',
            background: activeCategory === category ? '#0a84ff' : 'rgba(255, 255, 255, 0.08)',
            color: activeCategory === category ? 'white' : 'var(--text-secondary)',
            fontSize: 13,
            fontWeight: activeCategory === category ? 600 : 500,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
            transition: 'all 200ms ease',
            flexShrink: 0,
          }}
          className="hover:opacity-80"
          title={category}
        >
          {category}
        </button>
      ))}
    </div>
  )
}
