import type { Metadata } from 'next'
import './globals.css'
import { QueryProvider } from '@/components/providers/QueryProvider'
import { ToastProvider } from '@/components/ui/Toast'

export const metadata: Metadata = {
  title: 'Numera — AI-First Financial Spreading Platform',
  description: 'Autonomous financial spreading and covenant intelligence platform',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </head>
      <body>
        <QueryProvider>
          <ToastProvider>{children}</ToastProvider>
        </QueryProvider>
      </body>
    </html>
  )
}
