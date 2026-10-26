# 审计完整性修复与全量回归维护实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:executing-plans 按任务执行，先验证失败再修复。当前仅制定计划；未修改生产代码或业务数据，不提交或推送 GitHub。

**Goal:** 修复3项已复现审计缺陷，使操作日志可降级、对象归属正确、请求内容可解释；更新10项失败用例，并为18项跳过测试给出可验证的处置。

**Architecture:** 分为两个有独立验收门槛的工作包：A为审计修复，B为回归维护和隔离数据。审计事件构造与可失败的元数据补充解耦，半成品释放采用显式目标解析，请求正文与业务快照输入分别管理；业务库存、结算、权限规则不变。

**Tech Stack:** Java / Spring MVC、AOP、事务 / JdbcTemplate / MySQL / JUnit、Mockito / Vue / TypeScript / Playwright / Node.js。

**Spec:** 本文“规则与边界”为修复规格。事实依据为 `.runtime/audit-and-full-frontend-2026-10-23/delivery-result.json`、`reproductions.txt`、`release-audit-scope-evidence.tsv`、`adjustment-audit-input-evidence.tsv`。三个最小失败用例保存在同目录 `AuditReviewReproductionTest.java`。

## 全局约束

- 不修改生产计算、库存、领料、还库、结算、冲销和工厂隔离规则，不恢复此前隐藏的入口。
- 不新增数据库字段或迁移。审计上下文、事件JSON和请求展示结构允许兼容扩展，旧事件及旧日志必须继续可读。
- 不回填历史审计身份、工厂或对象，不根据同号ID、手机号或当前对象名称猜测历史归属。
- 不修改、重置现有账号密码和角色权限；全部权限和登录验证使用本轮临时角色及账号。
- 沿用请求4096字符、变更快照16KB及现有脱敏约束；超限须明确标记，不能以截断结果假装完整。
- 成功业务的outbox与业务写入保持同事务；失败审计继续独立事务，再降级到文件。不得以“忽略审计写入错误”允许原本应回滚的业务提交。
- 使用隔离数据并清理，测试审计保留。大数据分页只在独立库及独立服务执行，不向当前业务库灌入一万条资料。
- 不提交GitHub。实施后报告实际通过、失败、跳过、退役和未完成项，不承诺所有故障下绝对零丢失。

## 基线与验收口径

上轮3项审计专项全部通过，但额外故障注入复现3项缺陷。前端主运行332项为304通过、9失败、19跳过；演示用例单独启用失败后，按不同用例合并为304通过、10失败、18跳过。

10项失败分为8项用例前置步骤或定位过时、2项固定账号凭据不可用；这不是对相关业务链路已经通过的认证，必须更新后完整执行，不能只让原失败步骤通过。18项跳过是17项旧隐藏冲销入口、1项缺少大数据前置数据。

退役用例不计为通过。新增、拆分和退役会改变总数，交付须提供原332项与新用例的映射，不能强行维持332这个数字。

## 审查重点

1. 数据库在身份解析之后、事件构造之前失联，仍应尝试审计独立事务及文件兜底。
2. 库存、释放、仓库ID相同或跨厂碰撞，不得造成错误快照或日志可见范围扩大。
3. 同时存在路径ID、HTTP正文、服务参数、文件元数据时，不得丢失原因，也不得序列化凭据或文件内容。
4. 业务最终回滚、提交失败、重复提交、文件重放必须保留真实结果及同一事件ID。
5. 浏览器超时、工作线程被终止和种子构造中途失败后，仍能独立清理本次数据，不能依赖单个test的finally。

## 工作包A：3项审计缺陷

### 任务A1：恢复数据库异常下的审计降级

**原因：** `AuditRecorder.failure` 先调用 `event`；`event` 查询工厂名称抛出后，尚未进入 `persistFailure`，因此独立outbox写入和文件兜底均未执行。`TraceFilter` 只记录捕获失败。

**修改文件：**
- `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/audit/AuditRecorder.java`
- `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/audit/AuditContext.java`（仅需要缓存可信名称等字段时）
- `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/audit/AuditDelivery.java`（兼容名称缺失事件及回放）
- 新增 `backend/ruoyi-framework/src/test/java/com/ruoyi/framework/audit/AuditCaptureFallbackTest.java`
- 回归 `AuditCommitFailureTest`、`AuditFallbackReplayTest`、`AuditCapacityRecoveryTest`、`AuditDeliveryTest`。

- [ ] 将现有“工厂名称查询异常”失败复现转成常规回归，修复前确认失败。
- [ ] 将核心事件组装与工厂名称补充分开。名称补充失败不妨碍生成eventId、traceId、已确认人员/工厂ID、操作、结果及脱敏输入；业务归属仍来自可信数据库关系或已确认上下文，不能使用客户端factoryId猜测。
- [ ] 名称优先使用事件时已捕获的可信值；无法取得则留空或明确未记录。回放不得以当前名称冒充事件时名称，也不得再因名称缺失而阻断投递；旧格式事件继续兼容。
- [ ] 核心事件生成后执行原独立outbox事务；数据库不可用时将同一eventId写入文件。不能让元数据读取异常提前跳过该链路。
- [ ] 文件兜底失败须有可观测告警，包含事件/追踪ID及异常类别，不打印完整事件、凭据或业务快照，不把未落盘状态伪装成成功。原业务响应与提交/回滚策略不变。
- [ ] 测试：仅名称查询故障、整个DB故障、独立事务启动失败、恢复后重放、同事件重复文件行、文件写入失败、无工厂/多工厂/已删除工厂、最终提交回滚。恢复后至多形成一条最终日志，不生成虚假的成功审计。

**验收：** 原故障注入不再在名称查询处逃逸；确认文件写入尝试及内容脱敏，真实DB恢复后能投递同一事件。磁盘也不可用时明确告警并报告限制。

### 任务A2：修复半成品释放与撤销的对象和工厂归属

**原因：** `/metal/warehouse/` 通配匹配把库存ID及释放ID当作warehouse_id；ID不存在时缺对象，碰撞时读到无关仓库。服务返回的long释放ID也不被现有仅识别String的逻辑捕获。

**修改文件：**
- `AuditRecorder.java`、`AuditContext.java`
- 新增 `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/audit/SemiReleaseAuditSnapshot.java`
- 新增 `backend/ruoyi-framework/src/test/java/com/ruoyi/framework/audit/SemiReleaseAuditTest.java`
- 更新 `AuditRecorderTest`、`InventoryAuditTest`及实际审计验收脚本。

**明确的数据规则：**
- 两条接口均以 `metal_semi_release` 为释放业务主对象。创建前无释放ID时只保留真实来源库存的before关联快照，不伪造释放记录。
- 新增路径 `/metal/warehouse/inventory/{inventoryId}/semi-release`：路径ID只解析成库存ID；保存后将服务返回的long或合法数字字符串作为releaseId，再查询释放记录并验证其inventory_id与原来源相同。
- 撤销路径 `/metal/warehouse/semi-release/{releaseId}/cancel`：路径ID只解析成释放ID；通过释放记录取得库存、工厂、原工单等关系，记录ACTIVE→CANCELLED的真实变化。
- 工厂归属来自实际库存/释放记录；对象信息包含releaseId、inventoryId、来源工单/批次及必要数量重量，释放业务编号使用真实releaseId字符串，不创建虚构仓储单号。

- [ ] 先加入两种同号碰撞失败用例，再实现显式路径解析。显式规则先于通用仓库匹配，普通仓库CRUD仍走原逻辑。
- [ ] 创建的before/after采用固定结构，明确区分关联库存与释放主对象；库存余额未变化也能清楚看到释放额度变化。保持不写实物库存流水的业务语义。
- [ ] 非超管读取审计快照时只查询其已确认授权工厂的目标；跨厂失败不先抓取对方快照，不把请求声称的工厂加入audit_scope。超管则记录目标真实工厂。
- [ ] 返回ID仅允许Long/Integer等受控整数类型或合法数字字符串；不能把任意Number、DTO或Map不经验证当作对象ID。释放记录不存在、关联不一致、失败或回滚时不生成成功after快照。
- [ ] 真实接口验证：本厂可查、他厂列表不可见且详情拒绝；超管操作仍有正确业务工厂；幂等重放不创建第二份释放，并保留真实重放标记。
- [ ] 覆盖库存ID=仓库ID、释放ID=仓库ID、同号跨厂仓库、目标不存在、权限拒绝、原工单关闭、撤销前后及原仓库CRUD回归。

**历史处理：** 仅扫描已知释放路径下对象类型或归属异常的事件/追踪ID，给出人工核对清单；不自动改写日志或补造缺失日志。

### 任务A3：完整捕获多参数操作的实际输入

**原因：** `AuditServiceAspect` 只序列化服务第一个参数；当签名为 `(id,input)` 时c.request已成为非空ID，`TraceFilter` 随后不再补实际正文。

**修改文件：**
- `AuditContext.java`、`AuditMetadataAspect.java`、`AuditServiceAspect.java`、`AuditRecorder.java`、`TraceFilter.java`
- 新增 `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/audit/AuditRequestCapture.java`
- 新增 `backend/ruoyi-framework/src/test/java/com/ruoyi/framework/audit/AuditRequestCaptureTest.java`
- 更新 `AuditServiceAspectTest`、`AuditRedactorTest`、真实工单/报价审计验收。

**捕获责任：**
- 在Controller已解析参数、进入业务服务前，从 `@RequestBody`/明确表单参数取得逻辑正文，从 `@PathVariable` 取得目标参数；用结构化字段分别保存body、parameters及files元数据。
- 不依赖Java编译时是否保留方法参数名；路径变量按Controller注解绑定，内部服务无HTTP正文时使用明确的参数适配，未知参数只保留受控的索引/类型摘要。
- 服务快照用的业务输入与日志展示JSON分开管理，不能把整个展示包装对象直接当作原DTO传给snapshot。requestId幂等定位及既有新增编号、报价返回Map定位必须保持。

- [ ] 先固化多参数调整只记录11而丢失原因的失败用例。
- [ ] 通过 `AuditRequestCapture` 合并正文和目标参数；HTTP正文已捕获时，内部/嵌套服务不能覆盖它。service-only调用也不得无条件只取第一个参数。
- [ ] 明确覆盖adjustStock、settle、reverseOrder、reverseBatch、工单transition、partial-close/reopen、release/cancel等签名，保留reason、version、requestId、completedQuantity、数量和重量等有效输入。
- [ ] 确认MVC拦截器/Controller切面在服务outbox入队之前捕获正文，保留事务切面order=100与服务审计order=200；用真实MVC/接口测试验证顺序，不仅依赖直接调用mock切面。
- [ ] Controller之前的格式错误、校验或鉴权失败仍由TraceFilter做有界、脱敏的兜底捕获；不能重复消费请求流影响正常绑定。
- [ ] multipart只保留文件名、大小和摘要及允许的业务参数；不序列化Servlet对象、BindingResult、流、文件字节或认证上下文。
- [ ] 在入队和落盘前脱敏嵌套password/token/Authorization/cookie/验证码/手机号等信息；保留原4KB上限。长集合或超限内容显示截断标记，必要的短操作元数据优先保留，不把截断字符串当完整JSON。
- [ ] 旧日志继续以旧格式展示；新body/parameters结构的列表详情和导出可读，无需迁移旧记录。
- [ ] 验证成功、业务拒绝、版本冲突、事务回滚、重复请求、嵌套服务、无参数、普通单DTO和上传场景，包含隐私哨兵不得出现的断言。

## 工作包B：10项失败用例与18项跳过测试

### 任务B1：更新8项过时用例，保留有效断言

**公共文件：** 新增 `frontend/tests/helpers/people.ts`（人员选项选择与目标ID断言），维护 `tools/warehouse-test-fixture.mjs`、`tools/work-order-test-fixture.mjs` 的隔离准备能力。

| 用例 | 根因/阻塞位置 | 修改方案与完整验收 |
|---|---|---|
| calculation-live.spec.ts | 超管计算前未选工厂 | 使用本次隔离工厂，在计算前选择；继续核对自动体积、只读保存、多商品、输入变化使结果失效。同步审查后续步骤是否仍依赖旧布局。 |
| dashboard-live.spec.ts | 将工厂选择放在计算/保存之后 | 按当前流程先选工厂、再计算保存；保留价格快照、看板统计、本厂和跨厂断言。 |
| factory-live.spec.ts | 新增工厂未关联产线 | 超管先建未分配产线，在页面填写面积并必选该产线；保留缺项拒绝用例及新增/编辑/删除关联验证。 |
| master-live.spec.ts | API创建工厂传空产线 | 重排种子依赖：未分配产线→工厂绑定→其他资料；继续执行全部基础资料CRUD与权限，不只修首次创建。报价步骤亦按当前工厂上下文执行。 |
| inventory-edit-fields-live.spec.ts | 人员下拉仍按仅姓名精确定位 | 使用角色/姓名/手机号完整文案，在指定下拉面板选择；验证提交operatorId/keeperId正确，保留库存资料锁定、调整及流水断言。 |
| inventory-organization-live.spec.ts | 默认生产出库尚未选择工单上下文就添加明细 | 把通用仓库范围测试放在合法的非生产原料出库类型；生产出库另选工单、产品明细、工序后继续。候选文案更新为分类/来源展示，保留同厂多仓和跨厂隔离验证。 |
| stock-availability-live.spec.ts | 人员选项文案过时 | 使用公共人员选择帮助方法，继续覆盖超重量、超件数、重复行、最新余额和保存的后端权威校验。 |
| warehouse-live.spec.ts | 人员选项文案过时；后续含旧报工查看入口 | 更新人员定位，并审阅整条流程；已结算查看走当前工序消耗弹窗，继续核对批次/单据/物料和损耗，禁止恢复隐藏入口来满足旧断言。 |

- [ ] 保留上一轮失败日志和截图，先逐项复现其阻塞步骤，再更新准备和选择方式。
- [ ] 选择人员不使用不受约束的`.first()`，不通过页面强制赋值绕过真实交互；使用具体面板、已知文案和用户ID进行验证。
- [ ] 不通过延长超时、删除业务断言、把失败改成skip或取消必填验证获得绿色结果。
- [ ] 每个文件更新后跑完整用例，检查首次失败之后尚未到达的旧断言。若出现真实业务缺陷，保留最小复现并单列报告，不继续归类为“旧测试”。

### 任务B2：替换2项失效固定账号前置数据

**文件：** `frontend/tests/factory-account-live.spec.ts`、`frontend/tests/home-demo-live.spec.ts`，新增 `tools/ui-identity-fixture.mjs`。

- [ ] 移除固定手机号/口令及旧 `.runtime/demo-identities.json` 作为必需前提；不尝试猜测或重置现有账号。
- [ ] 新工厂账号测试临时创建工厂、角色、账号与已知业务数据，明确授予需要的最小权限；记录已生成的工单和库存数量，再验证本厂页面和不能访问的入口。
- [ ] 演示测试用新的隔离角色/账号覆盖原四类身份，超管只使用环境中已配置的现有登录方式；不更改超管凭据。保留只读账号隐藏写按钮、不同角色导航和工厂范围断言。
- [ ] 口令随机生成，仅在受控内存/临时私有上下文传递，不写入可交付报告、公开截图或版本库；结束后退出测试会话并删除/归档临时账号和角色。
- [ ] 数据不足时由fixture明确构造，不能依赖“恰好一条”的历史列表或更改现有角色权限。

### 任务B3：正式处理17项已隐藏入口测试

**文件：** `frontend/tests/report-correction-lifecycle.spec.ts`，新增 `frontend/tests/retired/report-correction-lifecycle.manifest.json`，补充现行入口及服务层对应测试。

- [ ] 按17个旧测试名称建立映射，记录退役原因、原风险与替代测试位置；保持同文件中仍适用的现行测试继续执行。
- [ ] 先补等效安全覆盖：后退/路由离开、切工单/批次、关闭重开、迟到成功或失败、保存锁、重复确认、版本冲突、无权限及会话401。
- [ ] 当前可见仓储单据冲销入口用真实组件测试；已隐藏报工入口做“入口仍隐藏且不会发出冲销请求”的负向断言。后端服务/API继续验证权限、依赖及原子回滚，不能以UI隐藏替代服务保护。
- [ ] 替代覆盖通过后，将只针对旧入口的17项从活跃`.spec.ts`转入非执行的退役清单；不得计为17项通过，也不恢复旧入口。
- [ ] 若替代测试复现现行入口的新竞态，先记录为实际缺陷并报告，不移除失败断言或假定旧修复已覆盖所有入口。

### 任务B4：在独立环境完成1项一万条分页测试

**文件：** `frontend/tests/bulk-data-live.spec.ts`、`frontend/playwright.config.ts`、`tools/local-test-client.mjs`（保持默认地址兼容）；新增 `tools/bulk-regression-fixture.mjs`、`frontend/playwright.bulk.config.ts`。

- [ ] 使用新建隔离库，例如 `heavy_metal_bulk_<runUUID>`，执行现有迁移；使用独立后端及前端端口。测试配置可覆盖baseURL/API地址，原业务服务地址保持不变。
- [ ] 种子脚本校验数据库名、连接地址和本次runUUID，拒绝写入当前heavy_metal业务库；结束仅删除本次创建的测试库/停止本次进程，绝不清空或恢复覆盖业务库。
- [ ] 六类基础资料各至少10000条有效数据，按当前工厂/产线等依赖构造；业务列表准备至少两页有效数据，看板期望值来自本次种子清单。删除旧10410等固定历史断言。
- [ ] 基础资料可在独立库批量构造并做约束自检；相关生产业务样本走有效API。明确这是分页/规模测试，不把批量造表数据当作正常生产业务已通过的证据。
- [ ] 实际页面检查首/次页条数、总数、排序、无重复或遗漏、筛选后回第一页及清空；保存首屏和分页截图、加载时间等实测，不预设未经测量的性能结论。
- [ ] 大数据运行与普通实库回归使用独立配置和数据，避免多个测试进程抢同一份列表或修改共享模板、价格规则。
- [ ] 成功/失败均执行独立清理与复核，服务和数据库资源创建、停止、删除结果写入本次清单。

### 任务B5：防止超时测试残留

**文件：** 新增 `tools/test-fixture-registry.mjs`，接入两类fixture及Playwright全局收尾/独立清理命令。

- [ ] 每次成功创建资源后立即把runUUID、前缀、类型和ID登记到受控清单；fixture构造中途失败也能找到已创建对象。
- [ ] test的finally负责快速清理；独立全局收尾负责超时或工作线程退出后的补偿，不能依赖已经被终止的测试继续await。
- [ ] 清理先核验ID、前缀、归属和本轮登记，再按业务引用逆序处理；重复执行应幂等。不得只凭相似名称或时间范围批量删除。
- [ ] 覆盖正常结束、超时、初始化失败、清理中途失败及再次清理；确认工单/单据/库存/报价和临时基础资料均无有效残留，审计保留。

## 实施与验证顺序

1. 保存现有变更与失败证据，建立本轮执行记录；将3个最小失败用例纳入常规测试，确认RED。
2. 实施A1→A2→A3，每项单独测试并复核事务、scope和脱敏；三项共同的AuditContext改动统一兼容，避免请求结构被另一项覆盖。
3. 先完善B5清单与超时清理，再执行B1、B2，使8个旧用例和2个账号用例都完成有效验收。
4. 建立B3替代覆盖后正式退役旧测试；实施B4独立大数据环境并实际运行。
5. 执行后端完整回归、3项审计专项、前端全量、独立大数据测试、类型检查和生产构建，最后进行代码审查与本地部署验证。

**运行要求：**
- Maven必须显式传 `-Dmaven.test.skip=false -DskipTests=false`，不能把BUILD SUCCESS但Tests are skipped视为通过。
- 后端针对性命令从backend执行：`mvn -o -pl ruoyi-framework -am test -Dmaven.test.skip=false -DskipTests=false -Dtest=AuditCaptureFallbackTest,SemiReleaseAuditTest,AuditRequestCaptureTest,AuditRecorderTest,AuditServiceAspectTest,InventoryAuditTest,AuditCommitFailureTest,AuditFallbackReplayTest,AuditDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false`；随后执行完整后端测试。
- 新工单/报价审计专项每次生成新目标，使用并扩展 `tools/verify-audit-targets-live.mjs`，不得用已清理的旧目标文件充当新验收；队列专项仍使用随机前缀隔离表。
- 普通Playwright全量显式启用更新后的安全live用例，保留单工作线程或证明fixture可并行；大数据用独立配置运行。无前置数据必须明确失败/列为未完成，不能静默skip。
- 独立测试库的连接、端口和路径由本轮清单传递，绝不能把业务数据库故障注入到当前服务。

## 验收与交付门槛

| 范围 | 必须提供的证据 |
|---|---|
| 审计降级 | 数据库故障注入、脱敏文件、恢复投递和去重；磁盘失败告警 |
| 对象与归属 | 实际释放/撤销前后日志；本厂可见、跨厂拒绝；同号碰撞不串对象 |
| 请求正文 | 调整、结算、冲销、关闭、释放等成功/失败日志包含应有原因和版本；隐私哨兵不存在 |
| 原10项失败 | 每个完整用例的最终结果、截图及未达步骤；不能只修定位就宣称全链路通过 |
| 原17项退役 | 风险→新测试映射和通过证据；退役数量独立列示 |
| 大数据 | 六页真实10000条、分页与筛选结果、测量数据和独立库清理结果 |
| 数据与环境 | 临时账号/角色/业务/基础资料无有效残留；审计保留，原凭据和权限未变 |

实施时先记录本地部署前版本和配置；无需数据库迁移。全部验证后打包重启本地后端，检查登录、库存、工单、审计列表/详情和导出关键接口。若业务数据库不可用等故障注入，只在独立环境执行。

交付报告分别列出：3项修复状态、后端及审计专项数量、普通前端结果、大数据结果、退役清单、失败/未执行原因、历史待核对事件及验证限制。已有错误或丢失的历史审计不自动补写，不作“历史数据已全部修复”承诺。本计划当前尚未执行，不提交GitHub。
