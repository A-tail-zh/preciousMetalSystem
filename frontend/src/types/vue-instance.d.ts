import 'vue'
declare module 'vue' {
  interface ComponentCustomProperties {
    $modal: typeof import('@/plugins/modal').default
    $tab: typeof import('@/plugins/tab').default
    $auth: typeof import('@/plugins/auth').default
    $cache: typeof import('@/plugins/cache').default
    $download: typeof import('@/plugins/download').default
    download: typeof import('@/utils/request').download
    useDict: typeof import('@/utils/dict').useDict
    parseTime: typeof import('@/utils/ruoyi').parseTime
    resetForm: typeof import('@/utils/ruoyi').resetForm
    handleTree: typeof import('@/utils/ruoyi').handleTree
    addDateRange: typeof import('@/utils/ruoyi').addDateRange
    getConfigKey: typeof import('@/api/system/config').getConfigKey
    selectDictLabel: typeof import('@/utils/ruoyi').selectDictLabel
    selectDictLabels: typeof import('@/utils/ruoyi').selectDictLabels
  }
}
