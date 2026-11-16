# 工单列表账号隔离实施报告

## 修改结果

- 列表按 sessionReady、账号 ID、Token、Cookie 及有效权限建立上下文；身份变化同步清空行、勾选、总数、加载和错误状态，回到第 1 页，保留16/32/64分页规格。
- 合并连续身份和分页事件；读取使用独立轮次与分页副本。过期成功、失败和 finally 均不能覆盖当前页面；公共请求层当前会话401处理继续生效。
- 监听 focus、storage、visibilitychange；跨标签Cookie失效或不一致时清空数据，不覆盖另一标签的新Cookie；普通有效focus不重复查询，卸载清理监听。
- 详情路由期间不额外查询列表，返回时按当前身份读取。查看、新增、编辑、删除均复核即时权限与目标。
- 删除确认与已提交请求使用独立操作轮次；旧确认不能以新账号提交，旧响应不能弹消息、改分页或触发新账号重载。已到达后端的操作仍以实际执行结果和审计为准。
- 代码审查发现并修复Cookie-only切换时旧工单表单仍可提交的问题：同步关闭/清空表单，打开各阶段及提交前后核验身份和Cookie，旧响应和保存状态失效。
- 390px分页改为自然换行，避免左右裁切；1920/1440/768/390px截图已检查。

## 验证证据

- 先观察账号切换残留用例失败，修复后通过；审查发现的Cookie-only表单2例也先失败再通过。
- 初轮相关回归116/116通过；最终列表33项和表单6项专项39/39通过。
- 最终真实账号/跨工厂接口及浏览器14项通过，隔离业务、账号和角色已清理，保留审计。证据位于 .runtime/work-order-list-session/live/result.json 与 list-*.png。
- 最终代码类型检查及生产构建已通过，退出码0；新增/编辑、权限提示与工厂下拉相关补充回归55/55通过（含重复验证的6项表单会话用例，不累计为独立用例）。
- 完整前端回归主套件515项：514通过，1项失败，0跳过，23.9分钟，命令退出码1。失败项为产品页768px初始页面/表格等待5秒超时，未进入输入布局断言；该用例独立连续复测3次全部通过，退出码0，未能稳定复现。原始失败日志保留，不能表述为主套件首轮全绿。
- 最终清单79个文件、521个独立用例：主套件515项，加主套件启动后新增且单独通过的6项表单会话用例。所有用例均已执行，当前无可复现失败；重复专项、连续复测与真实账号检查不重复累计到521项。
- 全量运行登记的39组隔离fixture全部标记清理完成；手动真实账号fixture也完成清理，审计保留。
- 独立审查：发现1项P1边界问题并修复；复审无新增可确认问题。

## 范围与限制

仅修改前端列表、表单以及相应API可选上下文参数；保留URL与请求体兼容。不修改后端业务、数据库或库存/结算规则。按计划未重复全量后端打包和数据库迁移；真实服务的跨厂直接查询已验证拒绝。

未提交或推送GitHub。本轮修改、计划内验证和审查已完成；未确认到剩余功能缺陷。产品页曾出现一次启动超时，已如实记录及复测。

## 修改文件

- frontend/src/views/metal/work-order/index.vue
- frontend/src/views/metal/work-order/WorkOrderForm.vue
- frontend/src/api/metal/work-order.ts
- frontend/tests/work-order-list-session.spec.ts（33项）
- frontend/tests/work-order-form-session.spec.ts（6项）
- tools/verify-work-order-list-accounts-live.mjs

## 审查问题与处置

P1：只改Cookie而Pinia未切换时，旧编辑工单可用新Cookie提交。独立审查以mock请求捕获到 PUT Authorization=B、body仍为A工单；本地新增2个失败用例确认后修复。最终6项专项覆盖已加载、打开中、提交中的成功/失败响应，真实浏览器另验证弹窗关闭。独立复审无新增可确认问题。

窄屏问题：390px分页总数和跳转字段被裁切；先用坐标断言确认失败，增加本页限定换行后通过，四个尺寸测试也包含边界断言。

## 未执行项目

本轮未重新执行全量后端单元/集成测试或独立千条数据性能套件，原因是计划范围仅为前端列表/表单会话隔离，后端业务未改动；真实本地服务及跨厂接口拒绝已验收。未重启后端、未迁移数据库、未提交或推送GitHub。

## 原始结果位置

- .runtime/work-order-list-session/full.log、frontend-progress.jsonl（主套件原始失败与514通过）
- .runtime/work-order-list-session/failed-recheck.log（失败项连续3次复测通过）
- .runtime/work-order-list-session/final-focused.log（39通过）
- .runtime/work-order-list-session/related.log（116通过）
- .runtime/work-order-list-session/form-related.log（55通过）
- .runtime/work-order-list-session/live-final.log、live/result.json（最终14项真实账号检查及清理）
- .runtime/work-order-list-session/typecheck-final.log、build-final.log
- .runtime/work-order-list-session/verification-summary.json（数量、退出码、源码哈希）
