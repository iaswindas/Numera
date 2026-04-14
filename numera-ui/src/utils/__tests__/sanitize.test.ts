/**
 * @jest-environment node
 */

// sanitize.ts relies on DOMPurify which needs window/document
// In a node test environment, we mock DOMPurify
jest.mock('dompurify', () => ({
  __esModule: true,
  default: {
    sanitize: (dirty: string, _opts?: unknown) => {
      // Simple mock: strip script tags
      return dirty.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
                   .replace(/on\w+="[^"]*"/gi, '')
    },
  },
}))

import { sanitizeHtml } from '../sanitize'

describe('sanitizeHtml', () => {
  it('strips script tags', () => {
    const input = '<p>Hello</p><script>alert("xss")</script>'
    expect(sanitizeHtml(input)).not.toContain('<script>')
  })

  it('preserves safe HTML', () => {
    const input = '<b>Bold</b> and <i>italic</i>'
    const result = sanitizeHtml(input)
    expect(result).toContain('<b>Bold</b>')
    expect(result).toContain('<i>italic</i>')
  })

  it('handles empty input', () => {
    expect(sanitizeHtml('')).toBe('')
  })
})
