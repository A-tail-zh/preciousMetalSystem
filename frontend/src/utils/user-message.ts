/** Unknown technical messages stay in logs rather than leaking English internals to the UI. */
export function userMessage(value:unknown,fallback='操作失败，请重试'){const message=typeof value==='string'?value.trim():'';return /[\u3400-\u9fff]/.test(message)?message:fallback}
