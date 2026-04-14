import type { NextConfig } from 'next'
import path from 'path'

const nextConfig: NextConfig = {
  output: undefined,
  outputFileTracingRoot: path.join(__dirname, '../'),
  webpack: (config) => {
    // pdfjs-dist requires canvas which is not available in browser
    config.resolve.alias.canvas = false
    return config
  },
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
      },
      {
        source: '/ws/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/ws/:path*`,
      },
    ]
  },
  experimental: {
    // Enable server actions
  },
}

export default nextConfig
