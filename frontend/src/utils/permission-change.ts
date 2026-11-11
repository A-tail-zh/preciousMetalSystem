export const PERMISSION_CHANGED = 'permissions-changed'
const storageKey = 'permissions-changed'

export function notifyPermissionChange() {
  try { localStorage.setItem(storageKey, String(Date.now())) } catch { /* same-tab listeners still receive the event below */ }
  window.dispatchEvent(new Event(PERMISSION_CHANGED))
}

export function isPermissionChange(event: StorageEvent) {
  return event.key === storageKey
}

export function changesAccess(url = '', method = '') {
  const path = url.split('?')[0]
  if (!['post', 'put', 'delete'].includes(method.toLowerCase())) return false
  if (/^\/system\/role(\/|$)/.test(path)) return true
  if (/^\/system\/menu(\/|$)/.test(path)) return true
  if (/^\/system\/user\/authRole(\/|$)/.test(path)) return true
  return /^\/system\/user(\/|$)/.test(path) && !/\/(list|export|importData|importTemplate|profile|resetPwd|deptTree)(\/|$)/.test(path)
}
