'use client'

import React from 'react'

type Props = { children: React.ReactNode }
type State = { hasError: boolean }

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="card">
          <div className="card-title">Something went wrong</div>
          <p style={{ color: 'var(--text-muted)', marginTop: 8 }}>Please refresh the page or try again.</p>
        </div>
      )
    }
    return this.props.children
  }
}
