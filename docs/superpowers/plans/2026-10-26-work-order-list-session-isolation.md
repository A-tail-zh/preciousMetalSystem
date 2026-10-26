# 工单列表账号隔离 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. 已按计划实施并验证；未提交或推送GitHub。

**Goal:** 工单列表在账号、Token、登录状态或有效权限变化后立即清除旧数据；新账号有权时重新读取自己的数据，旧请求及旧操作不能影响新页面。

**Architecture:** 沿用useActionPermission与请求层isContextCurrent机制，在列表页建立授权上下文、读取票据和独立操作票据。授权变化同步清空状态，重新读取合并调度；跨标签事件仅检查会话有效性，避免有效会话重复请求。保持已有后端鉴权与工厂隔离。

**Tech Stack:** Vue 3、TypeScript、Pinia、Element Plus、Axios、Playwright；现有本地8080/5173服务。

**Spec:** docs/superpowers/reports/2026-10-26-settled-followup-code-review.md；已复现证据 .runtime/settled-followup-review/list-account-switch.json。

## Global Constraints

- 不提交、不推送GitHub，保留现有未提交修改。
- 不修改URL、工单状态规则、数据库结构、账号策略或工厂隔离规则；默认不改后端、不迁移、不重启后端。
- 不信任角色名称；基于sessionReady、有效权限、账号ID及Token/Cookie一致性判断。
- 沿用原列表分页规格16/32/64。身份变化回到第1页，保留有效pageSize；普通翻页沿用原行为。
- 不把清空按钮或隐藏操作列当作清空数据。rows、selected、total、加载/错误状态和表格内部勾选均须处理。
- 不清除或覆盖另一标签页的新Token，不自动调用logout，不使用轮询。
- 已发送且后端执行成功的操作不能被前端回滚；只阻止失效确认继续提交、阻止旧响应产生新页面副作用，不自动重试写操作。

## Review Focus

- A→B→A：账号相同也不能让第一轮旧响应回填；用读取轮次加身份快照证明，任务1覆盖。
- Cookie已经变更而Pinia仍保留旧账号：清空旧列表且不发授权上下文不一致的请求，任务2覆盖。
- 权限撤销再恢复、sessionReady短暂false：立即失效，恢复后合并重读，无请求风暴，任务1/2覆盖。
- 删除确认框跨越账号切换：旧确认不能使用新账号凭据提交原目标，任务2覆盖。
- 列表与详情共享父组件：身份变化可以清空缓存，但详情中不额外查列表；返回时读取当前账号，任务2覆盖。

## 文件职责

- 修改 frontend/src/views/metal/work-order/index.vue：授权失效、缓存清空、请求隔离、事件清理及操作保护。
- 修改 frontend/src/api/metal/work-order.ts：orderList、必要的orderDelete增加可选isContextCurrent；旧调用兼容。
- 新增 frontend/tests/work-order-list-session.spec.ts：账号、权限、分页、迟到响应与旧操作回归。
- 新增 tools/verify-work-order-list-accounts-live.mjs：真实账号/跨工厂的浏览器验证与隔离数据清理。
- 复用 frontend/src/composables/useActionPermission.ts、frontend/src/utils/request.ts，不默认修改公共鉴权逻辑。
- 核对WorkOrderForm已有onChange失效机制；只补证据充分的缺口，不为重构增加新公共组件。

### Task 1: 列表身份失效与读取隔离

**Interfaces:**
- orderList(params: {pageNum:number;pageSize:number}, isContextCurrent?:()=>boolean)，返回结构不变。
- 列表内部canReadList():boolean、clearList():void、invalidateAccess():void、scheduleReload():void，内部名称可按项目风格微调，职责不得混合。
- 授权快照包含账号ID、Token、有效权限及sessionReady；Cookie通过getToken实时比较。

- [x] 新增失败用例：A列表已显示时切B，断言A行立即消失、勾选清空、total归零、pageNum回到1；B请求后只显示B行。用已收集证据复现当前失败。
- [x] 新增失败用例：A请求悬挂，切B并成功，再释放A成功/失败，B行、B总数、loading、错误提示均不被A覆盖。覆盖A→B→A及同账号换Token。
- [x] 新增失败用例：撤销list权限/退出登录/sessionReady=false，数据立即清空且无请求；恢复权限后重新读取。
- [x] 给orderList透传可选isContextCurrent，复用请求层现有失效拦截。保留当前会话401处理；旧会话401不能使B失效。
- [x] 实现读取守卫：请求前检查list权限、sessionReady、非空且匹配的Token/Cookie；复制分页参数，捕获账号、Token、读取票据及页面上下文。
- [x] 所有成功、catch、finally均检查同一current条件；失效操作不能弹旧错误、清除新loading或回填数据。
- [x] actionPermission.onChange同步增加票据并清空状态；实际查询合并到一次调度，执行前再校验授权。禁止在失效与清空之前等待nextTick或防抖。
- [x] 页面卸载时使票据失效并取消待执行调度。
- [x] 运行新用例，确认从失败变通过。

### Task 2: 跨标签、路由及旧操作边界

**Interfaces:**
- 列表复用任务1的授权与读取逻辑，事件回调不直接发送未经校验的请求。
- orderDelete(ids:string[], isContextCurrent?:()=>boolean)在确有需要时扩展，旧调用与请求结构不变。
- 写操作使用独立操作票据，避免普通GET翻页误判已发出的删除响应；授权、路由失效则递增操作票据。

- [x] 新增失败用例：实际两个浏览器标签共享Cookie，另一标签退出/切换登录；旧列表在focus、storage或visibilitychange时清空，无旧身份请求，不删除新Cookie。
- [x] 为事件设置对称注册/卸载。有效会话普通focus不重复查列表；同一会话失效事件反复触发不产生查询或重复弹窗。
- [x] 详情路由下清空列表缓存但不查列表；从详情返回时按当前账号读第1页。进入详情/离开页面要让旧列表请求失效。
- [x] 复核查看、新增、编辑及删除入口的即时权限。WorkOrderForm已有身份变化关闭逻辑优先复用；不假设隐藏按钮能取消旧回调。
- [x] 删除确认前捕获账号、Token、目标ID和操作票据；确认返回后再检查身份、权限、路由和目标上下文，失效则不发DELETE。
- [x] 删除请求后的提示、分页修正、重新加载、deleting状态都受操作票据保护。旧成功或失败不能影响新账号；不补发、不重试旧操作。
- [x] 账号切换中如果操作已到服务端，保留服务端实际结果和审计，不承诺前端取消等于后端撤销。
- [x] 覆盖关闭页面、删除确认中切账号、删除响应晚到、快速往返详情等用例，逐项复测。

### Task 3: 真实账号验收、回归与独立审查

- [x] 用现有fixture创建A/B不同工厂账号及各自工单；不使用现存用户数据，不修改管理员凭据。
- [x] 真实登录验证：A列表只见A工单，切B后不残留A记录；B无列表权限时清空且不发请求。跨厂直接查询继续由既有后端拦截。
- [x] 验证同工厂不同权限账号、账号相同Token更换、权限撤销恢复及勾选状态；所有复现记录保存请求次数、响应归属和截图。
- [x] 检查1920、1440、768、390px下分页、空数据、错误提示及操作列。确保编辑按钮仍按此前要求在已结算报工中隐藏，查看正常。
- [x] 先跑新专项与既有报工/会话/列表相关回归，再运行完整前端套件，启用现有真实服务开关；以执行时实际收集数量报告，不沿用历史通过数。
- [x] 前端验证命令（在frontend目录）：node node_modules/@playwright/test/cli.js test tests/work-order-list-session.spec.ts；完整套件使用playwright.config.ts及现有LIVE环境。
- [x] 类型检查：node node_modules/vue-tsc/bin/vue-tsc.js --noEmit；生产构建：node node_modules/vite/bin/vite.js build。均要求退出码0。
- [x] 本计划不改后端业务代码，因此不重复运行全量后端打包；真实接口与跨厂校验必须执行。若实施发现必须改后端，先补范围与验证要求，不能将前端通过当作后端已验证。
- [x] 独立审查授权捕获、请求轮次、写操作确认边界、监听清理、旧调用兼容。发现问题按失败复现→最小修复→复测处理。
- [x] 清理隔离账号与业务数据，核对残留，保留审计。前端源码开发服务自动更新，后端不因本计划重启。
- [x] 交付报告列明修复文件、实际通过数量、失败/跳过及原因、浏览器证据、限制和未完成项；不提交或推送GitHub。

## 最终验收标准

1. 任何账号或授权变化后，旧行、旧勾选、旧总数立即失效。
2. 新账号有权才读取，列表只呈现当前账号的有效结果。
3. 旧请求不能覆盖新结果、制造错误弹窗或改变新页面loading。
4. 跨标签退出后旧列表停止读取；新Cookie不被旧页面清除。
5. 旧删除确认不能在新账号下提交，旧写响应不能改变新页面。
6. 列表、详情、分页及既有报工查看回归正常，独立审查无未修复的已确认问题。

实施完成：任务1/2/3均已执行；全量515项首轮514通过、1项启动等待超时，失败项连续复测3次通过；新增6项表单专项通过，最终覆盖521个独立用例。实施记录：docs/superpowers/reports/2026-10-26-work-order-list-session-implementation.md。
