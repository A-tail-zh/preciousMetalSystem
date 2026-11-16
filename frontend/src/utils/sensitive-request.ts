/** Do not persist credential-bearing bodies in browser storage. */
export function containsSensitiveData(value: unknown): boolean {
  if (value instanceof FormData) { let sensitive = false; value.forEach((_,key) => { if (/password|passwd|pwd|token|secret|authorization|credential|captcha/i.test(key)) sensitive = true }); return sensitive }
  if (typeof value === 'string') {
    try { return containsSensitiveData(JSON.parse(value)) } catch { return /(?:password|passwd|pwd|token|secret|authorization|credential|captcha)\s*(?:=|:|%3[dD])/i.test(value) }
  }
  if (Array.isArray(value)) return value.some(containsSensitiveData)
  if (value && typeof value === 'object') return Object.entries(value).some(([key, field]) => /password|passwd|pwd|token|secret|authorization|credential|captcha/i.test(key) || containsSensitiveData(field))
  return false
}
