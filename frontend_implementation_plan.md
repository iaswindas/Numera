# Numera Frontend — Implementation Specification

> **This document is an AI-implementable specification.** Every component, hook, store, and configuration is specified precisely enough for autonomous implementation.

---

## 0. Spreadsheet Component Decision

### The Problem

The spreading workspace needs an Excel-like grid that supports:
- 200+ rows with category grouping (expand/collapse)
- Cell editing with expression builder
- Confidence-based cell coloring (green/amber/red)
- Bidirectional navigation with PDF viewer
- Custom cell renderers (currency, percentage, ratio)
- Keyboard navigation (arrow keys, Tab, Enter)
- Copy/paste support
- Virtual rendering (performance)

### Options Evaluated

| Library | License | Formula Engine | Cell Editing | Virtual Rendering | Custom Renderers | Verdict |
|---|---|---|---|---|---|---|
| **AG Grid Community** | MIT ✅ | ❌ (Enterprise) | ✅ | ✅ | ✅ | **Best option** |
| Jspreadsheet CE | MIT ✅ | ✅ Basic | ✅ | ✅ v5 | ⚠️ Limited | Good fallback |
| ReactGrid | MIT ✅ | ❌ | ✅ | ❌ | ✅ | Too basic |
| Univer | Apache 2.0 ⚠️ | ✅ | ✅ | ✅ | ✅ | License changed recently, risk |
| TanStack Table | MIT ✅ | ❌ (headless) | DIY | DIY | DIY | Too much work |
| Custom Canvas | N/A | DIY | DIY | DIY | DIY | Too much work |

### Recommendation: AG Grid Community (MIT)

**Why AG Grid Community works for us:**

1. **MIT licensed** — free for commercial use, no per-developer fees
2. **Cell editing** built-in with keyboard nav, Tab, Enter, Escape
3. **Virtual rendering** handles 200+ rows effortlessly
4. **Custom cell renderers** — we build our own confidence-colored cells
5. **Row grouping** via `groupDisplayType` (works in Community for tree data)
6. **Column pinning** — pin the "Label" column on the left
7. **Copy/paste** — built-in clipboard support

**What AG Grid Community does NOT have (and how we solve it):**

| Missing Feature | Our Solution |
|---|---|
| Formula engine | We don't need it in the grid — formulas run server-side in `FormulaEngine.kt` |
| Pivot tables | Not needed for spreading |
| Range selection | We implement our own via custom cell click handlers |
| Excel export with styles | Use `exceljs` library separately |
| Row grouping (tree) | Use `treeData` mode (available in Community) with custom `getDataPath` |

**Our formulas DON'T run in the spreadsheet.** They run in the backend. The grid is a **data display + editing surface**, not a full spreadsheet engine. This is why AG Grid Community is perfect — we need a grid, not Excel.

---

## 1. Technology Stack (2026 SOTA)

| Layer | Technology | Version | Why |
|---|---|---|---|
| **Framework** | Next.js (App Router) | 15.x | Server Components, Server Actions, streaming |
| **React** | React | 19.x | `use()` hook, Suspense, transitions |
| **Language** | TypeScript | 5.7+ | Strict mode, `satisfies` operator |
| **Server State** | TanStack Query | v5 | Caching, optimistic updates, background sync |
| **Client State** | Zustand | v5 | Lightweight, no boilerplate, devtools |
| **Spreadsheet Grid** | AG Grid Community | 33.x | MIT, virtual rendering, cell editing |
| **PDF Viewer** | PDF.js | 4.x | Mozilla's PDF renderer + Canvas overlay |
| **Styling** | CSS Modules + Design Tokens | — | Scoped styles, no utility class bloat |
| **Animations** | Framer Motion | 12.x | Smooth micro-interactions |
| **Primitives** | Radix UI | latest | Accessible, unstyled, composable |
| **Icons** | Lucide React | latest | Consistent, tree-shakable |
| **Charts** | Recharts | 3.x | Already in the project |
| **Forms** | React Hook Form + Zod | latest | Performant, schema-validated |
| **HTTP** | Fetch (built-in) | — | No Axios needed with TanStack Query |
| **WebSocket** | Native WebSocket | — | Real-time processing status |
| **Font** | Inter (variable) | — | Professional banking typography |

### Why Next.js 15 Instead of Keeping Vite

| Concern | Vite SPA | Next.js 15 |
|---|---|---|
| SEO (login page, marketing) | ❌ Client-rendered | ✅ Server-rendered |
| Initial load speed | ⚠️ Full bundle download | ✅ Stream HTML + selective hydration |
| API proxy | Manual CORS setup | ✅ Built-in `rewrites` or Server Actions |
| Auth | Manual token handling | ✅ Server-side session via middleware |
| Code splitting | Manual `lazy()` | ✅ Automatic per-route |
| Image optimization | Manual | ✅ `next/image` |
| Deployment | Static hosting only | ✅ Vercel, Docker, Node.js anywhere |

### Migration Path from Current Vite Prototype

The existing 15 page components in `numera-ui/src/pages/` will be migrated:
1. Copy component logic into Next.js `app/(dashboard)/[route]/page.tsx`
2. Split into Server Components (data fetching) + Client Components (interactivity)
3. Replace inline styles with design tokens
4. Add TanStack Query hooks for API calls
5. The existing `Layout.tsx` becomes `app/(dashboard)/layout.tsx`

---

## 2. Project Structure

```
numera-ui/
├── next.config.ts
├── package.json
├── tsconfig.json
├── .env.local
├── public/
│   ├── fonts/
│   │   └── Inter-Variable.woff2
│   └── logo.svg
│
├── src/
│   ├── app/                                    # ── Next.js App Router ──
│   │   ├── layout.tsx                          # Root layout (font, metadata)
│   │   ├── globals.css                         # Design tokens + reset
│   │   ├── (auth)/                             # Auth route group (no sidebar)
│   │   │   ├── layout.tsx
│   │   │   └── login/
│   │   │       └── page.tsx
│   │   │
│   │   └── (dashboard)/                        # Dashboard route group (with sidebar)
│   │       ├── layout.tsx                      # Sidebar + header + main
│   │       ├── page.tsx                        # Dashboard home
│   │       ├── customers/
│   │       │   ├── page.tsx                    # Customer search
│   │       │   └── [customerId]/
│   │       │       ├── page.tsx                # Customer detail
│   │       │       └── items/
│   │       │           └── page.tsx            # Existing items
│   │       ├── documents/
│   │       │   └── page.tsx                    # File store
│   │       ├── spreading/
│   │       │   └── [spreadId]/
│   │       │       └── page.tsx                # Spreading workspace
│   │       ├── covenants/
│   │       │   ├── page.tsx                    # Covenant dashboard
│   │       │   ├── [customerId]/
│   │       │   │   └── page.tsx                # Covenant items
│   │       │   └── management/
│   │       │       └── page.tsx                # Covenant management
│   │       ├── reports/
│   │       │   └── page.tsx
│   │       ├── admin/
│   │       │   ├── users/
│   │       │   │   └── page.tsx
│   │       │   ├── taxonomy/
│   │       │   │   └── page.tsx
│   │       │   ├── templates/
│   │       │   │   └── page.tsx
│   │       │   └── formulas/
│   │       │       └── page.tsx
│   │       └── settings/
│   │           └── page.tsx
│   │
│   ├── components/                             # ── Shared Components ──
│   │   ├── ui/                                 # Design system primitives
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Select.tsx
│   │   │   ├── Dialog.tsx                      # Radix-based modal
│   │   │   ├── Dropdown.tsx
│   │   │   ├── Badge.tsx
│   │   │   ├── Toast.tsx
│   │   │   ├── Skeleton.tsx
│   │   │   ├── Tooltip.tsx
│   │   │   ├── Tabs.tsx
│   │   │   ├── DataTable.tsx                   # Generic sortable/filterable table
│   │   │   └── Pagination.tsx
│   │   │
│   │   ├── layout/                             # Layout components
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   ├── BreadcrumbNav.tsx
│   │   │   └── ThemeToggle.tsx
│   │   │
│   │   ├── spreading/                          # Spreading workspace components
│   │   │   ├── SpreadGrid.tsx                  # AG Grid wrapper (main grid)
│   │   │   ├── SpreadGridToolbar.tsx
│   │   │   ├── CellRenderers/
│   │   │   │   ├── ValueCellRenderer.tsx       # Confidence-colored value cell
│   │   │   │   ├── LabelCellRenderer.tsx       # Row label with indent
│   │   │   │   ├── ExpressionCellRenderer.tsx  # Shows expression formula
│   │   │   │   └── ConfidenceBadge.tsx
│   │   │   ├── CellEditors/
│   │   │   │   ├── ValueEditor.tsx             # Numeric input
│   │   │   │   └── ExpressionEditor.tsx        # Expression builder modal
│   │   │   ├── ExpressionBuilder.tsx           # Visual formula builder
│   │   │   ├── ValidationPanel.tsx             # Pass/fail validation checks
│   │   │   ├── MappingProgress.tsx             # "87/142 mapped (61%)" bar
│   │   │   └── VersionHistory.tsx              # Git-log version list + diff
│   │   │
│   │   ├── document/                           # Document viewer components
│   │   │   ├── PdfViewer.tsx                   # PDF.js renderer
│   │   │   ├── ZoneOverlay.tsx                 # Canvas overlay for zone boxes
│   │   │   ├── ValueHighlight.tsx              # Highlight mapped values
│   │   │   ├── PageThumbnails.tsx              # Page nav sidebar
│   │   │   └── ZoneEditor.tsx                  # Manual zone draw/resize
│   │   │
│   │   ├── dashboard/                          # Dashboard widgets
│   │   │   ├── RecentSpreads.tsx
│   │   │   ├── StatsCards.tsx
│   │   │   └── AccuracyGauge.tsx
│   │   │
│   │   └── common/                             # Shared utilities
│   │       ├── SearchInput.tsx
│   │       ├── FileUpload.tsx
│   │       ├── StatusBadge.tsx
│   │       ├── ConfirmDialog.tsx
│   │       └── EmptyState.tsx
│   │
│   ├── hooks/                                  # ── Custom Hooks ──
│   │   ├── useAuth.ts
│   │   ├── useCurrentUser.ts
│   │   ├── useWebSocket.ts
│   │   └── useDebounce.ts
│   │
│   ├── stores/                                 # ── Zustand Stores ──
│   │   ├── authStore.ts
│   │   ├── spreadStore.ts                      # Active spread workspace state
│   │   ├── documentStore.ts                    # PDF viewer state
│   │   └── uiStore.ts                          # Theme, sidebar, modals
│   │
│   ├── services/                               # ── TanStack Query Hooks ──
│   │   ├── api.ts                              # Base fetch wrapper
│   │   ├── authApi.ts                          # Login, refresh, me
│   │   ├── documentApi.ts                      # Upload, status, zones
│   │   ├── customerApi.ts                      # CRUD, search
│   │   ├── templateApi.ts                      # Templates, line items
│   │   ├── spreadApi.ts                        # CRUD, process, values, versions
│   │   └── auditApi.ts                         # Event log queries
│   │
│   ├── types/                                  # ── TypeScript Types ──
│   │   ├── auth.ts
│   │   ├── customer.ts
│   │   ├── document.ts
│   │   ├── template.ts
│   │   ├── spread.ts
│   │   ├── audit.ts
│   │   └── api.ts                              # Pagination, error, etc.
│   │
│   └── lib/                                    # ── Utilities ──
│       ├── constants.ts
│       ├── formatters.ts                       # Currency, number, date
│       ├── validators.ts                       # Zod schemas
│       └── cn.ts                               # className merger
```

---

## 3. Package Dependencies

```json
{
  "name": "numera-ui",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev --turbopack",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "next": "^15.3.0",
    "react": "^19.1.0",
    "react-dom": "^19.1.0",

    "ag-grid-react": "^33.1.0",
    "ag-grid-community": "^33.1.0",

    "pdfjs-dist": "^4.10.38",

    "@tanstack/react-query": "^5.68.0",
    "@tanstack/react-query-devtools": "^5.68.0",

    "zustand": "^5.0.3",

    "@radix-ui/react-dialog": "^1.1.6",
    "@radix-ui/react-dropdown-menu": "^2.1.6",
    "@radix-ui/react-tooltip": "^1.1.8",
    "@radix-ui/react-tabs": "^1.1.4",
    "@radix-ui/react-select": "^2.1.6",
    "@radix-ui/react-popover": "^1.1.6",
    "@radix-ui/react-toast": "^1.2.6",

    "framer-motion": "^12.6.0",
    "lucide-react": "^0.475.0",
    "recharts": "^3.8.1",
    "react-hook-form": "^7.54.2",
    "@hookform/resolvers": "^4.1.3",
    "zod": "^3.24.2",
    "react-resizable-panels": "^2.1.8",
    "date-fns": "^4.1.0",
    "clsx": "^2.1.1"
  },
  "devDependencies": {
    "@types/node": "^22.13.0",
    "@types/react": "^19.0.8",
    "@types/react-dom": "^19.0.3",
    "typescript": "^5.7.3",
    "eslint": "^9.20.0",
    "eslint-config-next": "^15.3.0",
    "@next/eslint-plugin-next": "^15.3.0"
  }
}
```

---

## 4. Configuration

### next.config.ts

```typescript
import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // Proxy API requests to backend (avoids CORS in development)
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
      },
    ]
  },
  // Optimize fonts
  optimizeFonts: true,
  // Enable server actions
  experimental: {
    serverActions: {
      bodySizeLimit: '50mb',
    },
  },
  // PDF.js worker
  webpack: (config) => {
    config.resolve.alias.canvas = false
    return config
  },
}

export default nextConfig
```

### .env.local

```env
BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_APP_NAME=Numera
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

---

## 5. Design System

### globals.css — Design Tokens

```css
:root {
  /* ── Color Palette (Dark Mode Default — Bankers love dark) ── */
  --color-bg-primary: #0a0e17;
  --color-bg-secondary: #111827;
  --color-bg-tertiary: #1a2332;
  --color-bg-card: #151d2b;
  --color-bg-input: #1a2332;
  --color-bg-hover: #1e293b;

  --color-text-primary: #f1f5f9;
  --color-text-secondary: #94a3b8;
  --color-text-tertiary: #64748b;
  --color-text-muted: #475569;

  --color-border: #1e293b;
  --color-border-hover: #334155;
  --color-border-focus: #3b82f6;

  --color-accent: #3b82f6;         /* Blue */
  --color-accent-hover: #2563eb;
  --color-accent-light: rgba(59, 130, 246, 0.1);

  --color-success: #10b981;        /* Green */
  --color-success-bg: rgba(16, 185, 129, 0.1);
  --color-warning: #f59e0b;        /* Amber */
  --color-warning-bg: rgba(245, 158, 11, 0.1);
  --color-danger: #ef4444;         /* Red */
  --color-danger-bg: rgba(239, 68, 68, 0.1);

  /* ── Confidence Colors (Spreading Grid) ── */
  --color-confidence-high: #10b981;
  --color-confidence-high-bg: rgba(16, 185, 129, 0.08);
  --color-confidence-medium: #f59e0b;
  --color-confidence-medium-bg: rgba(245, 158, 11, 0.08);
  --color-confidence-low: #ef4444;
  --color-confidence-low-bg: rgba(239, 68, 68, 0.08);
  --color-confidence-unmapped: transparent;

  /* ── Zone Colors (PDF Overlay) ── */
  --color-zone-balance-sheet: #3b82f6;
  --color-zone-income-statement: #10b981;
  --color-zone-cash-flow: #f59e0b;
  --color-zone-notes: #8b5cf6;
  --color-zone-other: #6b7280;

  /* ── Typography ── */
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;

  --font-size-xs: 0.75rem;
  --font-size-sm: 0.8125rem;
  --font-size-base: 0.875rem;
  --font-size-lg: 1rem;
  --font-size-xl: 1.125rem;
  --font-size-2xl: 1.5rem;
  --font-size-3xl: 2rem;

  /* ── Spacing ── */
  --spacing-xs: 0.25rem;
  --spacing-sm: 0.5rem;
  --spacing-md: 0.75rem;
  --spacing-lg: 1rem;
  --spacing-xl: 1.5rem;
  --spacing-2xl: 2rem;

  /* ── Radii ── */
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --radius-xl: 12px;

  /* ── Shadows ── */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.3);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.4);

  /* ── Layout ── */
  --sidebar-width: 240px;
  --header-height: 56px;
}

/* ── Light Mode Override ── */
[data-theme="light"] {
  --color-bg-primary: #ffffff;
  --color-bg-secondary: #f8fafc;
  --color-bg-tertiary: #f1f5f9;
  --color-bg-card: #ffffff;
  --color-text-primary: #0f172a;
  --color-text-secondary: #475569;
  --color-border: #e2e8f0;
}
```

---

## 6. Zustand Stores (Complete State Shape)

### authStore.ts

```typescript
interface AuthState {
  accessToken: string | null
  user: {
    id: string
    email: string
    fullName: string
    roles: string[]
    tenantId: string
    tenantName: string
  } | null
  isAuthenticated: boolean

  // Actions
  login: (email: string, password: string, tenant: string) => Promise<void>
  logout: () => void
  refreshToken: () => Promise<void>
  setToken: (token: string) => void
}
```

### spreadStore.ts

```typescript
interface SpreadState {
  // Active spread
  spreadItemId: string | null
  documentId: string | null
  templateId: string | null
  status: SpreadStatus

  // Grid state
  activeCell: { itemCode: string; columnIndex: number } | null
  selectedCells: string[]         // Array of itemCodes
  editingCell: string | null
  showFormulaCells: boolean
  showVariance: boolean
  showCurrency: boolean

  // PDF ↔ Grid navigation
  highlightedSourcePage: number | null
  highlightedSourceCoords: BoundingBox | null
  highlightedZoneId: string | null

  // Mapping status
  mappingCoverage: number         // 0-100
  highConfidenceCount: number
  mediumConfidenceCount: number
  lowConfidenceCount: number
  unmappedCount: number

  // Version
  currentVersion: number
  isDirty: boolean

  // Actions
  setActiveCell: (cell: ActiveCell | null) => void
  setEditingCell: (itemCode: string | null) => void
  highlightSource: (page: number, coords: BoundingBox) => void
  clearHighlight: () => void
  markDirty: () => void
  reset: () => void
}
```

### documentStore.ts

```typescript
interface DocumentState {
  // PDF viewer
  currentPage: number
  totalPages: number
  zoom: number                    // 0.5 - 3.0
  rotation: number                // 0, 90, 180, 270

  // Zones
  zones: DetectedZone[]
  activeZoneId: string | null
  showZoneOverlays: boolean

  // Manual zone drawing
  isDrawingZone: boolean
  drawingRect: BoundingBox | null

  // Actions
  setPage: (page: number) => void
  setZoom: (zoom: number) => void
  setActiveZone: (id: string | null) => void
  toggleZoneOverlays: () => void
  startDrawingZone: () => void
  finishDrawingZone: (rect: BoundingBox) => void
}
```

### uiStore.ts

```typescript
interface UiState {
  theme: 'dark' | 'light'
  sidebarCollapsed: boolean
  activeSidebarItem: string
  toasts: Toast[]

  // Modals
  isExpressionEditorOpen: boolean
  isVersionHistoryOpen: boolean
  isValidationPanelOpen: boolean

  // Actions
  toggleTheme: () => void
  toggleSidebar: () => void
  addToast: (toast: Omit<Toast, 'id'>) => void
  removeToast: (id: string) => void
  openModal: (modal: string) => void
  closeModal: (modal: string) => void
}
```

---

## 7. TanStack Query Service Layer

### api.ts — Base Wrapper

```typescript
const BASE_URL = '/api'  // Proxied via next.config.ts rewrites

async function fetchApi<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const token = useAuthStore.getState().accessToken
  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (response.status === 401) {
    // Try refresh, if that fails redirect to login
    await useAuthStore.getState().refreshToken()
    // Retry original request with new token
  }

  if (!response.ok) {
    const error = await response.json()
    throw new ApiError(error.error, error.message, response.status)
  }

  return response.json()
}
```

### spreadApi.ts — Key Hooks

```typescript
// Fetch spread item with all values
export function useSpreadItem(spreadId: string) {
  return useQuery({
    queryKey: ['spread-item', spreadId],
    queryFn: () => fetchApi<SpreadItemResponse>(`/spread-items/${spreadId}`),
    staleTime: 30_000,
  })
}

// Trigger AI mapping
export function useProcessSpread() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (spreadId: string) =>
      fetchApi<MappingResult>(`/spread-items/${spreadId}/process`, { method: 'POST' }),
    onSuccess: (data, spreadId) => {
      queryClient.invalidateQueries({ queryKey: ['spread-item', spreadId] })
    },
  })
}

// Update a single cell value (optimistic update)
export function useUpdateSpreadValue(spreadId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ valueId, body }: { valueId: string; body: SpreadValueUpdate }) =>
      fetchApi(`/spread-items/${spreadId}/values/${valueId}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    // Optimistic update: immediately reflect in UI
    onMutate: async ({ valueId, body }) => {
      await queryClient.cancelQueries({ queryKey: ['spread-item', spreadId] })
      const previous = queryClient.getQueryData(['spread-item', spreadId])
      queryClient.setQueryData(['spread-item', spreadId], (old: any) => ({
        ...old,
        values: old.values.map((v: any) =>
          v.id === valueId ? { ...v, mappedValue: body.mappedValue } : v
        ),
      }))
      return { previous }
    },
    onError: (_err, _vars, context) => {
      queryClient.setQueryData(['spread-item', spreadId], context?.previous)
    },
  })
}

// Submit spread
export function useSubmitSpread() {
  return useMutation({
    mutationFn: ({ spreadId, comments }: { spreadId: string; comments: string }) =>
      fetchApi(`/spread-items/${spreadId}/submit`, {
        method: 'POST',
        body: JSON.stringify({ comments }),
      }),
  })
}

// Get version history (git log)
export function useSpreadHistory(spreadId: string) {
  return useQuery({
    queryKey: ['spread-history', spreadId],
    queryFn: () => fetchApi<VersionHistoryResponse>(`/spread-items/${spreadId}/history`),
  })
}

// Diff two versions
export function useSpreadDiff(spreadId: string, v1: number, v2: number) {
  return useQuery({
    queryKey: ['spread-diff', spreadId, v1, v2],
    queryFn: () => fetchApi<DiffResponse>(`/spread-items/${spreadId}/diff/${v1}/${v2}`),
    enabled: v1 > 0 && v2 > 0,
  })
}

// Rollback
export function useRollbackSpread() {
  return useMutation({
    mutationFn: ({ spreadId, version, comments }: { spreadId: string; version: number; comments: string }) =>
      fetchApi(`/spread-items/${spreadId}/rollback/${version}`, {
        method: 'POST',
        body: JSON.stringify({ comments }),
      }),
  })
}
```

---

## 8. Key Components — Detailed Specifications

### 8.1 Spreading Workspace Page

**Route:** `/spreading/[spreadId]/page.tsx`

**Layout:** Split-pane (horizontal, resizable)
- **Left pane (40-60%):** PDF document viewer with zone overlays
- **Right pane (40-60%):** AG Grid model grid with toolbar
- **Bottom bar:** Mapping progress + validation summary

```
┌─────────────────────────────────────────────────────────────────────┐
│ ← Back │ ENOC Annual Report 2025 │ Save Draft │ Submit │ ⋮ Menu    │
├─────────────────────────────┬───────────────────────────────────────┤
│                             │ Toolbar: Accept All | Variance | ⚙️   │
│   PDF VIEWER                │───────────────────────────────────────│
│                             │ Income Statement        2025    2024  │
│   [Page 4 of 42]            │   Revenue            12,500   11,200 │
│                             │   Cost of Sales      (8,100) (7,500) │
│   ┌───────────────────┐     │   ────────────────── ──────── ──────  │
│   │ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ │     │   Gross Profit       4,400    3,700 │
│   │ ▒ ZONE: P&L (96%)│     │   Operating Exp.    (2,100)  (1,900) │
│   │ ▒ [highlighted]  │     │   ────────────────── ──────── ──────  │
│   │ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ │     │   EBIT               2,300    1,800 │
│   └───────────────────┘     │   Finance Costs       (450)    (380) │
│                             │   PBT                 1,850    1,420 │
│   Page thumbnails:          │   Tax                  (370)    (284) │
│   [1] [2] [3] [4●] [5]     │   Net Profit          1,480    1,136 │
│                             │                                       │
├─────────────────────────────┴───────────────────────────────────────┤
│ ● 87/142 mapped (61%)  │ 72 high │ 10 medium │ 5 low │ 2 warns   │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 SpreadGrid Component (AG Grid Wrapper)

```typescript
// components/spreading/SpreadGrid.tsx
'use client'

import { AgGridReact } from 'ag-grid-react'
import { ValueCellRenderer } from './CellRenderers/ValueCellRenderer'
import { LabelCellRenderer } from './CellRenderers/LabelCellRenderer'

interface SpreadGridProps {
  values: SpreadValue[]           // From TanStack Query
  lineItems: ModelLineItem[]      // Template structure
  onCellValueChanged: (itemCode: string, newValue: number) => void
  onCellClicked: (itemCode: string, page: number, coords: BoundingBox) => void
  onAcceptAll: (threshold: 'HIGH' | 'MEDIUM') => void
}

// Column definitions:
const columnDefs = [
  {
    headerName: 'Line Item',
    field: 'label',
    pinned: 'left',
    width: 280,
    cellRenderer: 'labelCellRenderer',      // Shows indent + category icon
    sortable: false,
  },
  {
    headerName: '2025',
    field: 'currentPeriodValue',
    width: 140,
    cellRenderer: 'valueCellRenderer',      // Confidence-colored cell
    editable: (params) => params.data.itemType === 'INPUT',
    cellEditor: 'valueEditor',
    type: 'numericColumn',
  },
  {
    headerName: '2024',
    field: 'priorPeriodValue',
    width: 140,
    cellRenderer: 'valueCellRenderer',
    editable: false,                         // Prior period is read-only
    type: 'numericColumn',
  },
  {
    headerName: 'Expression',
    field: 'expressionDisplay',
    width: 200,
    cellRenderer: 'expressionCellRenderer', // Shows "Product Sales + Service Income"
  },
  {
    headerName: 'Conf.',
    field: 'confidenceScore',
    width: 70,
    cellRenderer: 'confidenceBadge',        // 92% with color dot
  },
  {
    headerName: 'Source',
    field: 'sourcePage',
    width: 70,
    cellRenderer: (params) => params.value ? `P${params.value}` : '—',
  },
]

// Row data is built by merging lineItems (template) + values (mapped data):
function buildRowData(lineItems: ModelLineItem[], values: SpreadValue[]): RowData[] {
  const valueMap = new Map(values.map(v => [v.itemCode, v]))
  return lineItems.map(item => {
    const value = valueMap.get(item.itemCode)
    return {
      itemCode: item.itemCode,
      label: item.label,
      category: item.category,
      itemType: item.itemType,
      indentLevel: item.indentLevel,
      isTotal: item.isTotal,
      currentPeriodValue: value?.mappedValue ?? null,
      priorPeriodValue: null,  // Loaded from historical spread
      expressionDisplay: value?.expressionDetail?.formula ?? '',
      confidenceScore: value?.confidenceScore ?? null,
      confidenceLevel: value?.confidenceLevel ?? null,
      sourcePage: value?.sourcePage ?? null,
      sourceCoords: value?.sourceCoordinates ?? null,
      isFormulaCell: item.itemType === 'FORMULA',
      isManualOverride: value?.isManualOverride ?? false,
    }
  })
}
```

### 8.3 ValueCellRenderer

```typescript
// components/spreading/CellRenderers/ValueCellRenderer.tsx
'use client'

export function ValueCellRenderer(params: ICellRendererParams) {
  const { data, value } = params
  if (value === null || value === undefined) {
    return <span className="cell-unmapped">—</span>
  }

  const bgColor = {
    HIGH: 'var(--color-confidence-high-bg)',
    MEDIUM: 'var(--color-confidence-medium-bg)',
    LOW: 'var(--color-confidence-low-bg)',
  }[data.confidenceLevel] || 'transparent'

  const borderColor = {
    HIGH: 'var(--color-confidence-high)',
    MEDIUM: 'var(--color-confidence-medium)',
    LOW: 'var(--color-confidence-low)',
  }[data.confidenceLevel] || 'transparent'

  const formatted = formatFinancialValue(value, data.isTotal)

  return (
    <div
      className="value-cell"
      style={{
        backgroundColor: bgColor,
        borderLeft: `3px solid ${borderColor}`,
        fontWeight: data.isTotal ? 700 : 400,
        fontStyle: data.isFormulaCell ? 'italic' : 'normal',
        color: data.isManualOverride ? 'var(--color-accent)' : 'inherit',
      }}
    >
      {formatted}
    </div>
  )
}

function formatFinancialValue(value: number, isTotal: boolean): string {
  const abs = Math.abs(value)
  const formatted = abs.toLocaleString('en-US', { maximumFractionDigits: 0 })
  return value < 0 ? `(${formatted})` : formatted
}
```

### 8.4 PDF Viewer with Zone Overlay

```typescript
// components/document/PdfViewer.tsx
'use client'

import * as pdfjsLib from 'pdfjs-dist'
import { useDocumentStore } from '@/stores/documentStore'
import { useSpreadStore } from '@/stores/spreadStore'

interface PdfViewerProps {
  documentUrl: string             // Pre-signed MinIO URL
  zones: DetectedZone[]           // From API
  onValueClicked: (text: string, page: number, coords: BoundingBox) => void
}

// Renders PDF page on <canvas>, then draws zone overlays on a second <canvas> layer
// Zone boxes color-coded:
//   Blue = Balance Sheet, Green = Income Statement,
//   Orange = Cash Flow, Purple = Notes

// When spreadStore.highlightedSourcePage changes:
//   1. Navigate to that page
//   2. Draw a yellow highlight box at highlightedSourceCoords
//   3. Animate the highlight (pulse effect)

// When user clicks inside a zone:
//   1. Detect which zone was clicked
//   2. Scroll the SpreadGrid to the corresponding zone section
//   3. Highlight the clicked text value in yellow
```

### 8.5 Expression Builder

```typescript
// components/spreading/ExpressionBuilder.tsx
// Modal dialog for building complex mapping expressions

// Layout:
// ┌──────────────────────────────────────────────┐
// │ Expression Builder — Revenue                  │
// ├──────────────────────────────────────────────┤
// │ Sources:                                      │
// │ ┌─────────────────────────────────────────┐  │
// │ │ [P4] Product Sales         7,200   [×]  │  │
// │ │ [P4] Service Income        2,100   [×]  │  │
// │ │ [P4] Commission            1,000   [×]  │  │
// │ └─────────────────────────────────────────┘  │
// │                                               │
// │ Operators: [+][-][×][÷]                       │
// │ Adjustments: [ABS][NEG][×1000][÷1000]        │
// │                                               │
// │ Preview: 7,200 + 2,100 + 1,000 = 10,300      │
// │ After unit scale (×1000): 10,300,000          │
// │                                               │
// │              [Cancel]  [Apply Expression]      │
// └──────────────────────────────────────────────┘

// Behavior:
// 1. Click a value in the PDF → it appears as a source row
// 2. Click operator buttons to set how sources combine
// 3. Apply adjustments (ABS, NEG, unit scale)
// 4. Live preview shows computed result
// 5. "Apply" saves expression to the cell via PUT /api/spread-items/{id}/values/{valueId}
```

### 8.6 Version History (Git Log)

```typescript
// components/spreading/VersionHistory.tsx
// Slide-out panel showing spread history

// Layout:
// ┌──────────────────────────────────┐
// │ Version History                   │
// ├──────────────────────────────────┤
// │ v3 • SUBMITTED                   │
// │ by Demo Analyst • 10:30 AM       │
// │ "Q4 2025 annual spread"          │
// │ 3 cells changed                  │
// │ [View] [Diff v2↔v3] [Rollback]  │
// │ ─────────────────────────────    │
// │ v2 • SAVED                       │
// │ by Demo Analyst • 10:15 AM       │
// │ 15 cells changed                 │
// │ [View] [Diff v1↔v2]             │
// │ ─────────────────────────────    │
// │ v1 • CREATED                     │
// │ by System • 10:00 AM             │
// │ "AI mapping completed"           │
// │ 95 cells mapped                  │
// │ [View]                           │
// └──────────────────────────────────┘

// "View" → loads spread state at that version (read-only mode)
// "Diff" → shows side-by-side cell changes (added/modified/removed)
// "Rollback" → confirm dialog → POST /api/spread-items/{id}/rollback/{version}
```

---

## 9. Bidirectional Navigation (PDF ↔ Grid)

### Grid → PDF (Click cell → show source)

```
1. User clicks a cell in SpreadGrid
2. SpreadGrid reads cell's sourcePage + sourceCoords
3. spreadStore.highlightSource(page, coords) is called
4. PdfViewer watches spreadStore.highlightedSourcePage
5. PdfViewer navigates to that page
6. ZoneOverlay draws a yellow pulsing box at coords
```

### PDF → Grid (Click value → show mapped cell)

```
1. User clicks a text value in the PDF
2. PdfViewer identifies the click coordinates
3. PdfViewer finds which zone contains the click
4. PdfViewer emits onValueClicked(text, page, coords)
5. SpreadingWorkspace searches values for matching sourcePage + sourceCoords
6. SpreadGrid scrolls to the matched row and highlights it
```

---

## 10. WebSocket Integration

```typescript
// hooks/useWebSocket.ts
// Connects to ws://backend:8080/ws/documents/{documentId}
// Receives processing status updates in real-time

interface WsMessage {
  type: 'PROCESSING_STATUS' | 'PROCESSING_COMPLETE' | 'PROCESSING_ERROR'
  documentId: string
  status: string                  // PROCESSING, OCR_COMPLETE, TABLES_DETECTED, etc.
  progress: number                // 0-100
  message: string
  tablesDetected?: number
  zonesClassified?: number
}

// Usage in FileStore/DocumentUpload:
// 1. Upload file via POST → get documentId
// 2. Open WebSocket connection to /ws/documents/{documentId}
// 3. Show progress bar + status text
// 4. On PROCESSING_COMPLETE → navigate to spreading workspace
```

---

## 11. Implementation Order

| Step | What | Files | Depends On |
|------|------|-------|------------|
| 1 | Init Next.js 15 project | `next.config.ts`, `package.json`, `tsconfig.json` | Nothing |
| 2 | Design tokens + globals.css | `globals.css` | Step 1 |
| 3 | Root layout + Inter font | `app/layout.tsx` | Step 2 |
| 4 | UI primitives (Button, Input, Badge, etc.) | `components/ui/*` | Step 2 |
| 5 | Auth store + login page | `stores/authStore.ts`, `app/(auth)/login/page.tsx` | Step 4 |
| 6 | API service layer + TanStack Query provider | `services/api.ts`, `providers.tsx` | Step 5 |
| 7 | Dashboard layout (sidebar + header) | `app/(dashboard)/layout.tsx`, `components/layout/*` | Step 4, 6 |
| 8 | Dashboard home page | `app/(dashboard)/page.tsx` | Step 7 |
| 9 | Customer search page | `app/(dashboard)/customers/page.tsx` | Step 7 |
| 10 | Customer detail + existing items | `app/(dashboard)/customers/[id]/*` | Step 9 |
| 11 | File store + upload page | `app/(dashboard)/documents/page.tsx` | Step 7 |
| 12 | WebSocket hook for processing status | `hooks/useWebSocket.ts` | Step 11 |
| 13 | Type definitions for all entities | `types/*` | Step 6 |
| 14 | TanStack Query hooks (all APIs) | `services/spreadApi.ts`, `services/documentApi.ts`, etc. | Step 13 |
| 15 | PDF viewer (render + zoom + navigation) | `components/document/PdfViewer.tsx` | Step 2 |
| 16 | Zone overlay (Canvas overlay on PDF) | `components/document/ZoneOverlay.tsx` | Step 15 |
| 17 | Spread store + document store | `stores/spreadStore.ts`, `stores/documentStore.ts` | Step 13 |
| 18 | SpreadGrid (AG Grid + custom renderers) | `components/spreading/SpreadGrid.tsx`, `CellRenderers/*` | Step 17 |
| 19 | Spreading workspace page (split-pane) | `app/(dashboard)/spreading/[id]/page.tsx` | Step 15, 16, 18 |
| 20 | Bidirectional navigation (PDF ↔ Grid) | Wire up stores between components | Step 19 |
| 21 | Expression builder modal | `components/spreading/ExpressionBuilder.tsx` | Step 18 |
| 22 | Version history + diff panel | `components/spreading/VersionHistory.tsx` | Step 18, 14 |
| 23 | Validation panel | `components/spreading/ValidationPanel.tsx` | Step 18, 14 |
| 24 | Admin pages (users, taxonomy, templates) | `app/(dashboard)/admin/*` | Step 7 |
| 25 | Covenant pages (dashboard, items, management) | `app/(dashboard)/covenants/*` | Step 7 |

---

## 12. Performance Targets

| Metric | Target |
|---|---|
| First Contentful Paint (FCP) | < 1.0s |
| Largest Contentful Paint (LCP) | < 2.0s |
| Time to Interactive (TTI) | < 2.5s |
| Cumulative Layout Shift (CLS) | < 0.05 |
| SpreadGrid render (200 rows) | < 50ms |
| PDF page render | < 200ms |
| Cell edit → optimistic update | < 50ms |
| Page navigation | < 100ms |
| Bundle size (JS, gzipped) | < 250KB initial |

---

## 13. Future Extensions (Phase 2+)

These components will be added in later phases without restructuring:

| Phase | Feature | Component |
|---|---|---|
| P1 | Split document view (two PDFs) | `components/document/DualPdfViewer.tsx` |
| P1 | OCR error correction overlay | `components/document/OcrCorrectionLayer.tsx` |
| P1 | Page operations (merge, split, rotate) | Server-side + `components/document/PageTools.tsx` |
| P2 | Covenant monitoring grid | `app/(dashboard)/covenants/monitoring/page.tsx` |
| P2 | Risk heatmap chart | `components/covenant/RiskHeatmap.tsx` |
| P2 | Waiver letter editor | `components/covenant/WaiverEditor.tsx` |
| P3 | BPMN workflow designer | `components/admin/WorkflowDesigner.tsx` |
| P3 | Report builder | `components/reports/ReportBuilder.tsx` |
| P4 | LLM copilot chat panel | `components/copilot/CopilotPanel.tsx` |
