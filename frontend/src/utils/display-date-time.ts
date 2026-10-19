/** Format server-local date times for display without parsing or shifting time zones. */
export function displayDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const text = String(value).trim()
  if (!text) return '—'
  const match = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?(?:\.\d{1,9})?$/.exec(text)
  return match ? `${match[1]} ${match[2]}:${match[3] ?? '00'}` : text
}
export function dateTimeCell(_row: unknown, _column: unknown, value: unknown): string {
  return displayDateTime(value)
}
