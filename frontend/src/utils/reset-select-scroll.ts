import { nextTick } from 'vue'

export async function resetSelectScroll(visible: boolean) {
  if (!visible) return
  await nextTick()
  requestAnimationFrame(() => {
    document.querySelectorAll<HTMLElement>('.el-select__popper .el-select-dropdown__wrap').forEach(panel => {
      if (panel.getClientRects().length) panel.scrollTop = 0
    })
  })
}
