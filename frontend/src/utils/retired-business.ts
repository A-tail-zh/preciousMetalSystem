// 旧模块重写期间停用；原实现备份见 .runtime/legacy-retirement-20261106/source-before.zip。
const retiredPaths = ['work-order', 'warehouse', 'warehouse-order', 'inventory', 'acid-wash-history', 'acid-wash', 'process-batch', 'warehouse-flows', 'route-skip-permits']

export function isRetiredBusinessRoute(path = '', component = '') {
  return retiredPaths.some(name => [path, component].some(value =>
    value === `metal/${name}` || value.startsWith(`metal/${name}/`) ||
    value === `/metal/${name}` || value.startsWith(`/metal/${name}/`)))
}
