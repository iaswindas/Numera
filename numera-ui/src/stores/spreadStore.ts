'use client'
import { create } from 'zustand'
import type { SpreadItem, BoundingBox } from '@/types/spread'

interface SpreadState {
  activeSpread: SpreadItem | null
  isLocked: boolean
  lockedBy: string | null
  selectedCellCode: string | null
  highlightedSourcePage: number | null
  highlightedSourceCoords: BoundingBox | null
  isDirty: boolean
  isSaving: boolean

  setActiveSpread: (spread: SpreadItem) => void
  selectCell: (code: string | null) => void
  highlightSource: (page: number, coords: BoundingBox) => void
  clearHighlight: () => void
  setIsDirty: (dirty: boolean) => void
  setIsSaving: (saving: boolean) => void
  reset: () => void
}

const defaultState = {
  activeSpread: null,
  isLocked: false,
  lockedBy: null,
  selectedCellCode: null,
  highlightedSourcePage: null,
  highlightedSourceCoords: null,
  isDirty: false,
  isSaving: false,
}

export const useSpreadStore = create<SpreadState>()((set) => ({
  ...defaultState,
  setActiveSpread: (spread) => set({ activeSpread: spread }),
  selectCell: (code) => set({ selectedCellCode: code }),
  highlightSource: (page, coords) =>
    set({ highlightedSourcePage: page, highlightedSourceCoords: coords }),
  clearHighlight: () =>
    set({ highlightedSourcePage: null, highlightedSourceCoords: null }),
  setIsDirty: (dirty) => set({ isDirty: dirty }),
  setIsSaving: (saving) => set({ isSaving: saving }),
  reset: () => set(defaultState),
}))
