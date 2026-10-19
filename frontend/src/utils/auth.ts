import {sessionChanged} from './session-expiry'
import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

export function setToken(token: string): string | undefined {
  const result=Cookies.set(TokenKey, token)
  sessionChanged(token)
  return result
}

export function removeToken(): void {
  Cookies.remove(TokenKey)
  sessionChanged()
}
