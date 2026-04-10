'use client'
import { create } from 'zustand'

interface UiState {
  theme: 'dark' | 'light'
  sidebarOpen: boolean
  activeModals: Set<string>

  setTheme: (theme: 'dark' | 'light') => void
  toggleTheme: () => void
  toggleSidebar: () => void
  openModal: (modal: string) => void
  closeModal: (modal: string) => void
  isModalOpen: (modal: string) => boolean
}

export const useUiStore = create<UiState>()((set, get) => ({
  theme: 'dark',
  sidebarOpen: true,
  activeModals: new Set(),

  setTheme: (theme) => {
    set({ theme })
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', theme)
    }
  },
  toggleTheme: () => {
    const next = get().theme === 'dark' ? 'light' : 'dark'
    get().setTheme(next)
  },
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  openModal: (modal) =>
    set((s) => ({ activeModals: new Set([...s.activeModals, modal]) })),
  closeModal: (modal) =>
    set((s) => {
      const next = new Set(s.activeModals)
      next.delete(modal)
      return { activeModals: next }
    }),
  isModalOpen: (modal) => get().activeModals.has(modal),
}))
