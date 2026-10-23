# 加工批次一次入库、一次还料确认与自动结算实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 不自动提交或推送 GitHub。

**Goal:** 每个新加工批次仅一次有效产出入库，随后一次提交全部还料或确认无余料，自动结算并开放后续合格领料。

**Architecture:** 新增批次执行策略和还料确认记录，以批次锁内校验作为权威。统一还料服务在同一事务内分组调用现有仓储记账规则、登记确认、生成报工和刷新工单状态；前端展示后端返回的阶段及可执行操作。

**Tech Stack:** Java / Spring Boot / JdbcTemplate / MySQL / Flyway；Vue 3 / TypeScript / Element Plus / Playwright。

**Spec:** `docs/superpowers/specs/2026-10-23-batch-single-output-return-design.md`。

## 全局约束

- 范围为生产批次绑定的 `SEMI_IN`、`FINISHED_IN` 和三类生产还库；外部入库、期初库存、成品退还维持原行为。
- 同工序允许分批，每批只有一次有效产出和一次有效还料确认；冲销记录保留审计但不计有效次数。
- 一次产出单可以包含多条合法明细，产品身份、工序及数量仍按当前批次校验；不能把半成品和成品两种单据分别各做一次。
- 有余料时一次提交全部类型、全部来源、全部仓库的待还明细。后台可生成多张单据，但只算一次批次还料确认。
- 无余料必须主动确认实物已核对，不创建空单、零重占位单或虚假库存流水。
- 允许批次实际产出小于计划；实际完成件数必须是正整数且等于有效产出件数合计。
- 未加工半成品还库仍恢复原来源库存及释放额度；加工余料仍人工选择原材料、客户沿用工单。
- 净投入及损耗公式、路线匹配、跳步许可、库存余额、释放依赖和正常完成策略保持原规则。
- 新策略从新建批次生效，已有批次保持原策略；不自动合并、结算、冲销或重写历史。
- 使用现有结算权限，不增加默认授权；保存入库权限不能替代结算权限。
- 所有写操作保持工厂隔离、版本、幂等、事务及审计要求；不自动提交 GitHub。

## 重点复审风险

1. 旧保存、导入、独立结算、内部调用绕过次数或还料确认；任务2、3覆盖。
2. 同时包含原料、半成品及加工余料，逐张保存时中途失败或锁顺序变化；任务3覆盖整组回滚及并发。
3. 产出6件后还回4件：本批结束但工序尚未足额，不能错误完成整工序或重复预占；任务4覆盖。
4. 无余料并不等于没有重量差额，不能把损耗补成还料或错误要求差额为0；任务3覆盖。
5. 自动结算后冲销、下游领用及自动释放依赖，不能只撤销部分还料；任务5覆盖。

## 一、目标流程与例子

### 1. 新策略批次阶段

| 阶段 | 允许操作 | 禁止操作 |
|---|---|---|
| 待产出入库 | 按现有规则领料、登记一次产出 | 无产出就确认结束、生成零件数报工 |
| 待还料确认 | 查看产出、填写全部还料或确认无余料 | 第二次产出入库、通过旧结算入口跳过确认 |
| 已结算 | 查询、后续合格领用、受控整批冲销 | 追加领料、还料、产出及重复确认 |
| 已冲销 | 只读追溯 | 继续往该批次记账 |

入库后尚未确认还料时，不把产出开放为已结算来源。领料仍沿用当前规则；最后确认时按最新领料、产出及版本重新核对，不能使用打开页面时的过期余额。

### 2. 数量与结算

- 实际完成件数由入库明细汇总，界面只读显示，后端重新计算。
- 入库数量达到批次计划，不提前结算；还料确认是必需步骤。
- 本批实际完成小于计划时，结算后释放未完成的计划预占，剩余数量允许另建批次；保留原批次计划和实际完成历史。
- 工序足额判断采用“本单有效已结算加工 + 合法外部接入”的现有覆盖规则，不仅检查本次入库数量。
- 不足额且不再继续生产，仍走已有“部分完工关闭”，不能自动当作正常完成。

例一：计划10件，一次入库10件，确认无余料后自动结算；当前工序无需再建批次，下一工序可领合格半成品。

例二：领半成品10件100g，产出6件58g，一次还回未加工半成品4件40g；本批完成6件、损耗2g。工序还剩4件需求，可另建批次，不自动标记足额完成。

例三：领100g、产98g，现场没有可回收实物，确认无余料后损耗2g，不要求还料2g。

### 3. 统一还料内容

| 还料类别 | 来源与目的 | 输入 |
|---|---|---|
| 原料还库 `RAW_RETURN` | 当前批次原料出库行；恢复原规则对应库存 | 来源行、净重及原有选填字段 |
| 半成品还库 `SEMI_RETURN` | 当前批次未加工半成品出库行；恢复原库存 | 来源行、正整数件数、正净重、原因 |
| 加工余料还库 `PROCESS_RAW_RETURN` | 本批加工粉末、边角料；生成现有回收原料库存 | 人工选择原材料、正净重、仓库、原因 |

单个批次可同时填写三类；每类可多行。原料和半成品可以来自多张生产出库单，原有“每张子单同来源单”的约束通过后台分组满足，不能让用户分多次提交绕过一次确认。

## 二、任务拆分

### Task 1：策略、状态及幂等模型

**Files:**
- Create: `backend/ruoyi-admin/src/main/resources/db/migration/V32__batch_single_output_return.sql`（实施前检查版本号占用，若已占用使用下一个新增版本，不修改发布迁移）。
- Create: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/BatchCompletionRules.java`。
- Create: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/BatchCompletionRequests.java`。
- Modify: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseService.java` 的 `createBatch`、批次查询。
- Test: `backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/BatchCompletionRulesTest.java`。

**Interfaces:**
- 新批次策略 `SINGLE_OUTPUT_RETURN`；迁移时现有批次为 `LEGACY`。DDL默认值不能让未来新建批次继续静默使用旧策略，`createBatch` 必须显式写新策略。
- 新表 `metal_batch_return_confirmation`：批次唯一键、工厂、请求UUID及指纹、`NONE/RETURNED`、状态、确认人、时间、版本、实物核对确认；新表 `metal_batch_return_document` 关联一次确认生成的所有仓储子单。
- `BatchCompletionRules.phase(batchStatus, outputCount, confirmationStatus)` 返回 `AWAITING_OUTPUT/AWAITING_RETURN/SETTLED/VOID`；历史批次单独返回旧模式。
- `BatchCompletionRequests.ReturnConfirmation`：`requestId`、`version`、`mode`、`physicalChecked`、`reason`、`documents`。每个document提供还料类型、仓库、日期、人员和物料行；不接受前端指定库存恢复目标或派生子单UUID。

- [x] 先补策略默认值、历史保留、阶段判定、NONE禁止携带明细、RETURNED必须有明细的失败测试。
- [x] 添加迁移及请求模型；确认唯一约束只代表一次业务确认，幂等重试不重复创建记录。
- [x] 重跑规则测试并用隔离库验证新旧批次策略及迁移可重启；不回填历史还料身份。

### Task 2：一次产出权威校验及入口一致性

**Files:**
- Modify: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseService.java` 的 `postInternal`、`autoBind`、`postLine`。
- Modify: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseImportService.java`。
- Modify: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/BatchCompletionRules.java`。
- Test: existing `WarehouseOutputQuantityTest.java`、`WarehouseImportServiceTest.java`；Create `BatchSingleOutputTest.java`，位于同一service测试目录。

**Interfaces:**
- `requireOutputAvailable(batchId)`：批次锁内统计 `POSTED` 的 `SEMI_IN/FINISHED_IN`，合计最多一张有效单据；先识别相同UUID重放，再校验次数和版本。
- 新模式产出禁止旧请求 `settle=true` 直接绕过还料；明确返回“请先确认全部还料或无余料”，不能静默忽略请求参数。
- 导入是编辑本次尚未提交清单，不算一次入库；已生效产出后禁止再次导入提交第二张产出单。

- [x] 补第二张同类型、换类型、并发不同UUID、相同UUID重试、同UUID不同内容、导入、错误产品和数量超额失败用例。
- [x] 在锁内统一校验，不能仅在候选或按钮层限制；保留非生产入库及旧模式行为。
- [x] 验证失败不增加单据、流水、库存、批次版本和报工；旧请求重放仍返回原结果。

### Task 3：一次还料确认及自动结算事务

**Files:**
- Create: `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/BatchReturnConfirmationService.java`。
- Create: `backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/BatchCompletionController.java`。
- Modify: `WarehouseService.java`、`RawReturnAllowance.java`、`ProcessRawReturnService.java`、`SemiFlowService.java` 的必要共用校验/记账接口。
- Test: Create `BatchReturnConfirmationTest.java`、`BatchReturnConfirmationConcurrencyTest.java` 于service测试目录。

**Interfaces:**
- `GET /metal/process-batch/{id}/completion-context`：策略、阶段、版本、产出件数、各类领料/还库/产出重量、可还来源、允许操作及原因。仅返回现有仓储/批次访问权限允许的必要信息。
- `POST /metal/process-batch/{id}/confirm-return`：接收 `ReturnConfirmation`，返回确认ID、子单ID/编号、批次状态/新版本、实际完成件数、损耗、下一工序提示及工单状态。
- `confirmReturn(Long batchId, ReturnConfirmation request)`：同一事务内完成整组还料、确认记录、报工、预警、工单刷新及现有自动释放任务登记。
- 抽取受控的内部记账入口复用现有 `postLine` 等规则；不能对外暴露可绕过权限的 `internal=true`，也不能使用HTTP逐张保存或独立事务。

- [x] 先补混合三类型、多来源单、多仓库、重复来源明细、共享重量额度、最后一张失败全回滚测试。
- [x] 获取工厂锁并按已有顺序锁工单、批次、来源库存及释放记录；同类ID排序。一次请求起始校验外部版本，内部子单版本更新不能使后续子单误报过期。
- [x] 根据类型、仓库、来源出库单和人员等必要维度拆子单；子单UUID由确认UUID及稳定分组键派生，顺序变化不能产生重复库存。
- [x] 先对整组做重量及件数预检，再记账并做最终校验：`产出+三类还库 ≤ 有效领料`；`产出件数 ≤ 半成品领用件数−半成品还库件数`。组内重复来源累计，不能逐行重复使用额度。
- [x] NONE要求已有一次产出、实物核对为真、没有还料明细；保留剩余差额作为损耗。不能依赖“可还额度为0”判断是否允许确认。
- [x] RETURNED要求全部所填还料通过权威规则，拒绝纯零重量占位确认；保留原料非负校验，但整组必须有真实还库。
- [x] 由有效产出件数合计调用现有结算逻辑，不信任外部传入的完成件数。不新增第二套损耗公式。
- [x] 最后确认必须持有现有仓储添加及 `metal:process-batch:settle` 权限，返回所有子单只读摘要；无结算权限人员仍可登记产出，最后步骤提示由有权限人员确认，不先写一半还料。
- [x] 补无余料有损耗、无产出、已结算/冲销、越权、跨厂、未知来源、加工余料客户不一致、重复/并发确认测试。
- [x] 新模式旧还库保存、导入最终保存及独立结算接口统一拒绝绕过，并提示统一入口；历史模式继续原路径。导入预览的新入口仅追加统一草稿，不生效记账。

### Task 4：批次结束、工序进度与下一步骤

**Files:**
- Modify: `WarehouseService.java` 的 `settleInternal`、`sumQuantity`、`createBatch`、`refreshOrder` 和生产候选查询。
- Modify: 工序进度及工单完成相关现有服务；`WorkOrderCompletionService.java` 仅在必要时调整调用边界。
- Test: 新建 `BatchCompletionProgressTest.java`，并回归现有 `SemiAutoReleaseServiceTest.java`。

**Interfaces:**
- 结算继续保持原批次计划数，实际完成数写入真实产出合计；开放剩余计划能力基于 `SETTLED.completed_quantity`，未结算仍按计划预占。
- 响应下一工序信息只是操作导航和可领资格，不预造批次、领料单或报工。

- [x] 覆盖10件一次完成、6件完成4件还回、多个批次累计足额、同产品不同明细、外部接入和合法跳步。
- [x] 核对结算前半成品不可作为已结算来源、结算后可领，跨工单仍要求释放且来源匹配。
- [x] 末工序足额且所有现有条件满足时正常完成；不足额保持生产中；正常完成的自动释放沿用现有策略和依赖保护。
- [x] 并发新建剩余批次不超预占；足额后拒绝再建批次，不能仅通过前端禁用。

### Task 5：冲销、历史模式及审计一致性

**Files:**
- Modify: `WarehouseService.java` 的单据冲销和 `reverseBatch`；新确认服务及现有审计记录路径。
- Test: 新建 `BatchConfirmationReversalTest.java`，回归现有释放、来源依赖及工单重量总账测试。

**Interfaces:**
- 新模式已结算确认组不能逐张冲销子单；统一提示走受控整批冲销，先检查下游/释放依赖。整批冲销时一起标记确认失效，反向恢复库存、释放额度和报工状态。
- 未结算、尚未确认还料的错误产出，可按现有允许的冲销规则撤销；只要无依赖且冲销成功，有效次数归零，允许重新登记一次。
- 相同UUID重放已冲销的原请求仍返回原记录及状态，不能重新生效；重做业务使用新UUID/新批次。

- [x] 先补已结算拆单冲销拒绝、整批回滚、来源被下游引用、自动释放已有额度/领用、历史批次多次单据保留的用例。
- [x] 新增审计事件记录还料确认方式、关联批次和单据；无余料也必须可追溯人员、时间、请求及确认内容。
- [x] GET、导出及旧幂等请求保持历史兼容，不自动将旧多次还料视为新模式确认。
- [x] 只读扫描已有OPEN批次的有效产出数、各类还库数和异常，交付列出历史待核对记录，不自动处理。

### Task 6：前端统一还料入口和引导

**Files:**
- Modify: `frontend/src/views/metal/warehouse-common/WarehouseOrderForm.vue`、`frontend/src/api/metal/warehouse.ts`。
- Modify: `frontend/src/views/metal/warehouse-common/WarehousePage.vue` 及其批次详情入口。
- Modify: `frontend/src/views/metal/work-order/WorkOrderDetail.vue` 的批次状态/导航展示。
- Create: `frontend/src/views/metal/warehouse-common/BatchReturnConfirmationDialog.vue`。
- Test: Create `frontend/tests/batch-single-output-return.spec.ts`，更新现有 `process-raw-return.spec.ts`、`warehouse-work-order-context.spec.ts` 等依赖旧流程的测试。

**Interfaces:**
- API增加 `batchCompletionContext(id,isContextCurrent)` 和 `confirmBatchReturn(id,payload,isContextCurrent)`，权威状态来自服务端。
- 新批次产出表单取消手填结算勾选与完成数量，改为只读“本次完成N件”，提示“入库后请确认全部还料或无余料”。已有批次继续旧界面，清晰标“历史批次流程”。
- 入库成功提供“登记全部还料”和“确认无余料”；也可关闭，之后从批次详情继续。关闭页面不能自动视为无余料。
- 统一弹窗用单一草稿容纳三种还料；每行选择类别、来源或回收原材料及必要仓库。能新增、编辑、删除、预览导入多行，但只一次最终生效。
- 提交按钮“确认全部还料并结算”/“确认无余料并结算”，显示入库件数、投入/产出/还料及预计损耗；明确需要核对实物。
- 结算后提示“本批已结束”；有剩余需求显示“本工序尚余N件”；有后续工序提供“去下一工序领料”，预选上下文但不自动保存。

- [x] 先加第二次入库被阻止、三类多行混合还料、无余料确认、无权限、历史模式和未完成草稿关闭的浏览器失败测试。
- [x] 候选按每行类别及来源加载；返回额度计算考虑整个草稿，编辑排除自身，导入包含已有待提交明细。
- [x] 加载禁提交；序号、目标ID、版本及路由上下文隔离请求和确认框。提交锁定目标，网络重试复用UUID；版本冲突保留草稿并要求刷新，不自动重试。
- [x] 结算后刷新列表、工序进度、候选、报工及工单状态；不把旧响应写入新批次。
- [x] 1920、1440、768、390px验证弹窗、内部横滚、长物料名、多仓库和确认按钮可见性。

### Task 7：完整回归、部署与交付

- [x] 后端先运行新增规则/事务/并发测试，再执行完整回归；必须启用本机支持的数据库、审计等专项测试，报告所有跳过及原因。
- [x] 前端先运行新增流程和受影响测试，再运行完整Playwright回归；旧模式测试明确使用历史批次，新模式测试按新确认链路编排，不能通过放宽权限或恢复旧入口让用例通过。
- [x] 执行 `npm.cmd run typecheck`、`npm.cmd run build:prod`，保存退出码和日志。
- [x] 后端以 `backend/pom.xml` 现有Maven模块和测试配置执行；实施时记录准确命令、实际测试数量、通过/失败/跳过数量，不预写成功结论。
- [x] 使用隔离账号及业务数据走完首工序到末工序：生产出库→一次产出→混合还料或无余料→自动结算→后续领料→末工序正常完成→剩余半成品自动释放。
- [x] 实际验证两次同时入库、两次还料确认、还料确认与他人领料并发；全部失败路径核对库存、子单、确认记录、报工和预警无部分落库。
- [x] 复审独立保存/导入/结算/冲销所有入口、工厂隔离和只读账号；不将浏览器按钮限制当作后端保证。
- [x] 测试后按隔离标记清理业务数据，审计日志按原规则保留；不清理历史工单或改账号权限。
- [x] 本地执行新增迁移前备份；迁移、后端构建及测试通过后重启本地后端，核对健康、迁移版本、旧批次和新批次关键接口。
- [x] 报告逐项完成状态、测试数量、历史待核对批次及未完成/验证限制；不自动提交或推送GitHub。

## 三、核心验收表

| 场景 | 预期 |
|---|---|
| 本批第一次产出入库 | 成功；状态待还料确认，暂不结算 |
| 第二次产出或半成品/成品换类型再入库 | 后端拒绝，不新增库存或流水 |
| 同UUID重试第一次产出 | 返回原结果，不计第二次 |
| 产出达到计划但尚未确认还料 | 批次保持OPEN，不能提前开放该产出领用 |
| 领100g、产98g、确认无余料 | 自动结算，损耗2g，无虚假还料单 |
| 领10件100g、还4件40g、产6件58g | 一次还料确认后本批完成6件、损耗2g；工序剩余需求可另建批次 |
| 原料+半成品+粉末分别多行、多来源 | 一次提交，按规则生成多个子单，共享额度正确 |
| 最后一行超额、跨厂、无权限或结算失败 | 整组还料与确认回滚，原有效入库保留 |
| 两个请求并发确认 | 最多一个新确认、一次报工，无超退或重复库存 |
| 实物核对未确认、NONE夹带明细 | 拒绝；不把差额自动认定成余料 |
| 所有物料未加工、没有产出 | 不生成零件数报工，提示受控完整批次冲销 |
| 结算后追加生产领料/产出/还料 | 拒绝 |
| 确认组单独冲销子单 | 拒绝并提示整批流程；有下游/释放依赖时先处理依赖 |
| 历史批次已有多次入库还库 | 原记录和原流程保留，不自动改写或确认 |
| 页面A切B、离开后确认A弹框 | 不发失效请求、不回写B |
| 末工序足额、仍有其他未结算批次 | 不提前完成整单或自动释放 |

## 四、当前完成情况

2026-10-23已完成本计划7个功能阶段、本地V32迁移及部署。后端420项、默认完整前端344项、真实接口37项通过；类型检查、构建、重启及健康检查通过。隔离数据清理，历史业务保留，未提交GitHub。

实现、审查修正和验证限制见 `docs/superpowers/reports/2026-10-23-batch-single-output-return.md`。范围外的人员并发新增死锁尚未修复，独立万条数据专项本轮未重跑。
