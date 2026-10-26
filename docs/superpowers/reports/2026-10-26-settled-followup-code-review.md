# 2026-10-26 报工与会话关联代码复审

本轮为只读审查与定向浏览器复现，未修改业务代码、业务数据，也未提交或推送GitHub。

## 发现：P2 工单列表没有随账号切换失效

位置：frontend/src/views/metal/work-order/index.vue 第34行的load，以及第40行的watch/onMounted。

列表只用业务epoch防乱序，没有监听账号ID、Token、sessionReady或有效权限变化。组件保持挂载，A账号已有列表后切到B账号，旧A行仍可见，且不自动读取B列表。未完成的旧请求同样没有身份守卫。

真实Chrome、模拟接口的定向复现：开始显示甲账号工单，更新Cookie、store.token、store.id及sessionReady为乙账号，再等待350ms，查询日志仍只有A；甲工单可见、乙工单不可见。未连接数据库写业务数据。

证据：.runtime/settled-followup-review/repro-list.mjs、list-account-switch.json、list-account-switch.png。

建议：在列表中监听授权上下文变化，递增请求票据并清空rows、selected、total；当前账号仍有权限时重新查询。请求和响应校验账号、Token和Cookie，并在跨标签焦点/存储/可见性变化时清除失效数据。不要仅隐藏按钮或修改后端权限。

这属于前端旧数据残留，不构成已经验证的后端越权。独立审查已复核同一问题。

## 其他审查结论

已结算报工编辑按钮已从模板移除；查看按钮的权限判断、详情与查看弹窗的会话隔离、批次/单据/物料递归下钻的上下文及生命周期机制，未发现新的充分证据问题。

本轮未重跑上轮83项相关回归、类型检查或构建；没有代码变更，新的结论基于静态审查与独立的浏览器复现。该P2尚未修复。
