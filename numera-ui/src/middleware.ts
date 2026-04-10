import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const PUBLIC_PATHS = ['/login', '/_next', '/favicon.ico']

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next()
  }

  const token = req.cookies.get('numera-auth')?.value
  const isAuthRoute = pathname === '/login'

  if (!token && !isAuthRoute) {
    return NextResponse.redirect(new URL('/login', req.url))
  }

  if (token && isAuthRoute) {
    return NextResponse.redirect(new URL('/dashboard', req.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|.*\\..*).*)'],
}
