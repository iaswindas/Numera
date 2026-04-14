import DOMPurify from 'dompurify'

export function sanitizeHtml(dirty: string): string {
  if (typeof window === 'undefined') return dirty
  return DOMPurify.sanitize(dirty, {
    ALLOWED_TAGS: ['b', 'i', 'u', 'em', 'strong', 'a', 'p', 'br', 'ul', 'ol', 'li', 'span', 'div', 'h1', 'h2', 'h3', 'h4', 'table', 'thead', 'tbody', 'tr', 'td', 'th'],
    ALLOWED_ATTR: ['href', 'target', 'style', 'class', 'id'],
    ALLOW_DATA_ATTR: false,
  })
}
