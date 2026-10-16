export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    try { await navigator.clipboard.writeText(text); return } catch { /* Try the user-gesture fallback. */ }
  }
  const field = document.createElement('textarea')
  field.value = text
  field.style.cssText = 'position:fixed;left:-9999px;top:0;'
  field.setAttribute('readonly', '')
  const focused = document.activeElement as HTMLElement | null
  document.body.appendChild(field)
  try { field.select(); if (!document.execCommand('copy')) throw new Error('复制失败') }
  finally { field.remove(); focused?.focus() }
}
