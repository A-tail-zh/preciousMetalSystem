import { computed, watch } from 'vue'
import useUserStore from '@/store/modules/user'

/** Reactive permission checks. Backend authorization and factory scope remain authoritative. */
export function useActionPermission() {
  const user = useUserStore()
  const has = (permission: string) => user.sessionReady &&
    (user.permissions.includes('*:*:*') || user.permissions.includes(permission))
  const hasAny = (permissions: string[]) => permissions.length > 0 && permissions.some(has)
  const hasAll = (permissions: string[]) => permissions.length > 0 && permissions.every(has)
  const isAdmin = computed(() => user.sessionReady && String(user.id) === '1')
  const onChange = (invalidate: () => void) => watch(
    () => JSON.stringify([user.sessionReady, user.token, String(user.id), [...user.permissions].sort()]),
    invalidate, { flush: 'sync' })
  return { has, hasAny, hasAll, isAdmin, onChange }
}
