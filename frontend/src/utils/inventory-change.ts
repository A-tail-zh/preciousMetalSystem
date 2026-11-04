/** Invalidation only: no inventory or account data is shared between tabs. */
export const INVENTORY_CHANGED = 'metal:inventory-changed'
export function notifyInventoryChanged(){
 window.dispatchEvent(new Event(INVENTORY_CHANGED))
 try{localStorage.setItem(INVENTORY_CHANGED,JSON.stringify({time:Date.now(),nonce:Math.random()}))}catch{/* Focus refresh covers browsers where storage is unavailable. */}
}
