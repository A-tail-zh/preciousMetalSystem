# 盘盈单冲销与半成品释放依赖保护实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:executing-plans 逐项实施；本计划已按后续授权实施，验收记录见《盘盈冲销释放依赖保护修复验收-2026-10-23.md》。不提交 GitHub。

**Goal:** 阻止半成品调整单冲销破坏有效释放关系，保留合法的半成品还库及批次整体冲销。

**Architecture:** 在仓储单据冲销的统一预检中，仅对 ADJUST 关联的半成品库存校验释放依赖；复用现有工厂锁、事务和实际释放记录。校验先于所有库存及单据写入，不在通用 balance 方法中增加一刀切限制。

**Tech Stack:** Java / Spring transactions / JdbcTemplate / MySQL / JUnit / Node API regression / Vue frontend。

**Spec:** 本文“根因、规则与边界”为本次修复规格；事实证据见 `.runtime/auto-release-positive-e2e-2026-10-23/adjustment-reversal-evidence.json`，复现脚本见 `tools/reproduce-auto-release-adjustment-reversal.mjs`。

## 全局约束

- 不新增数据库字段、迁移、接口参数、权限或前端业务入口。
- 不修改历史单据、释放记录、库存余额、损耗和工单状态；已存在异常仅只读扫描报告。
- 自动、手工释放使用同一依赖规则；不以 releaseMode 分支绕开保护。
- 新增保护仅针对半成品 ADJUST 冲销；普通原料、成品及生产批次整体冲销保留原业务规则。
- 测试仅使用隔离业务数据，清理测试业务数据，保留审计日志；不自动提交或推送 GitHub。

## 审查重点

1. 释放不产生实物流水，不能仅检查后续 stock ledger。
2. 多库存调整单任一来源被阻断时，全部写入都应回滚。
3. 自动释放、手工释放、取消释放、跨单领用与冲销并发时，不能使用等待工厂锁之前的额度快照。
4. 目标批次整体冲销必须能反向恢复库存与释放可用额度，不能被通用库存保护误拦。
5. 已领用完、已取消及异常负余额释放记录须明确处理，不能只看可用件数大于零。

## 根因、规则与边界

已复现顺序：首工序产出10件100g → 下工序领走10件100g → 原半成品库存盘盈4件40g → 最终产出10件98g并正常完成 → 自动释放4件40g → 冲销盘盈单成功。最终实物0件0g，但 ACTIVE 释放可用4件40g。

`WarehouseService.reverseOrder` 经 `reverseDocuments` 直接反向写入库存。现有后续依赖检查仅查实物流水；自动释放仅改变使用资格，无实物流水，因此被遗漏。`adjustStock` 对新调整有保护，冲销调整没有对应保护。

本次明确规则：半成品库存只要仍存在 ACTIVE 释放记录，就不允许冲销关联 ADJUST 单据。包括尚未领用、部分领用、已全部领用的 ACTIVE 记录。最后一种仍代表未解除的下游关系，不能因可用额度为零而放行。此限制不按释放时间、调整正负号或冲销后是否仍有足够库存放宽，避免推断某次释放依赖哪一笔混合库存。CANCELLED 记录不阻断；但现有后续单据、版本、来源、余额规则仍需通过。

处理顺序：无下游使用时先撤销释放，再按现有规则冲销调整；有下游使用时先按合法业务流程处理下游，使释放恢复为可撤销，再撤销释放。不能自动撤销释放、级联冲销或改写释放数量。取消 AUTO 释放后，同一完成代次不得自动重建；保留现有完成代次规则。

## 任务1：固化失败回归

**文件：**
- 修改 `tools/reproduce-auto-release-adjustment-reversal.mjs`，使其修复后明确断言业务拒绝、库存不变、释放不变、单据仍 POSTED、无新增冲销流水。
- 新增 `backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/WarehouseAdjustmentReversalTest.java`。
- 复用 `SemiFlowServiceTest.java`、`SemiReturnIntegrationTest.java`、`SemiAutoReleaseConcurrencyTest.java` 的测试组织和数据库隔离方法。

- [x] 保留真实复现中的操作顺序：必须在末次领料后盘盈，否则可能被已有后续实物流水检查拦截，不能证明新增保护生效。
- [x] 修复前执行复现脚本，记录实际200与期望拒绝的失败结果，不把脚本退出失败算作测试通过。
- [x] 补充单元测试：AUTO/MANUAL × 未使用/部分使用/全部使用 ACTIVE 均阻断；CANCELLED、无释放按原规则处理。

## 任务2：增加精确的冲销依赖预检

**文件：**
- 修改 `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/SemiFlowService.java`。
- 修改 `backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseService.java`。

**接口：** `SemiFlowService.guardAdjustmentReversal(long inventoryId)`：查询该库存 ACTIVE 释放关系；存在则抛出业务异常，无写入。异常只返回当前账号有权访问的库存标识、释放记录ID、模式及必要下游阻断单号。

- [x] 在 `reverseDocuments` 写入任何库存之前，从本次 ADJUST 单据的原始实物流水关联库存中提取 SEMI 库存ID，去重并按ID升序锁定。不得信任客户端库存、类别或释放ID。
- [x] 对全部目标库存执行 `guardAdjustmentReversal`。不要直接复用仅判断“尚有可用额度”的 `guardStock` 作为完整冲销判断。
- [x] 提示区分“存在未使用释放，先撤销释放”和“释放已有下游使用，先处理下游再撤销释放”；多个阻断来源有界展示，避免超长提示。
- [x] 保留现有单据状态、版本、退还引用、后续实物流水、库存非负与来源依赖检查；新增校验通过不表示可以绕过这些规则。
- [x] 不在 `balance` 中增加通用 ACTIVE 释放禁写；不拦截合法 PRODUCTION_OUT 批次冲销及 SEMI_RETURN 逆向恢复。
- [x] 通过整笔事务保证拒绝后：库存数量/重量/版本、调整单状态/版本、实物流水、释放记录及额度全部不变。

## 任务3：事务与并发验证

**文件：** 新增 `backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/WarehouseAdjustmentReversalConcurrencyTest.java`，按现有真实MySQL并发测试机制接入。

- [x] 沿用工厂锁作为与释放、领用、取消操作的共同串行边界；在取得工厂锁后重读单据、库存和释放关系。继续 READ_COMMITTED，禁止引入等待锁前的旧额度快照。
- [x] 记录本次新增库存锁顺序，与已有自动释放、手工释放及仓储写入核对；不在查释放后反向补取工厂锁。
- [x] 两连接受控测试：释放先成功则冲销拒绝；冲销先成功则后续释放按最新真实余额重新校验，不生成虚假额度。自动任务允许按现有规则记录无余额或待核对，不强制要求某一状态文案。
- [x] 覆盖手工释放/取消与冲销、跨单领用与冲销竞争；确认没有超扣、负未释放余额、部分写入或死锁后误报成功。
- [x] 多行调整中一行受阻：其他库存、流水及单据全部不变；重复冲销及过期版本仍按原规则拒绝。

## 任务4：接口与浏览器完整验收

| 场景 | 验收结果 |
|---|---|
| 已复现盘盈4件40g后AUTO释放再冲销 | 拒绝；实物4/40、释放4/40、未释放0/0；调整单仍有效 |
| 手工释放、部分领用、全部领用 | ACTIVE关系均阻断，明确处理路径 |
| 多条ACTIVE及部分CANCELLED | 任一ACTIVE即阻断，不因另一条取消而放行 |
| 全部释放已撤销 | 新保护放行，原有后续引用和余额检查继续执行 |
| 从无释放的正/负半成品调整 | 原有合法冲销通过；库存不足等仍拒绝 |
| 原料、成品调整冲销 | 原行为不变 |
| B半成品还库、B完整批次冲销 | A原库存和原释放额度正确恢复，不误拦 |
| A来源批次仍存在有效释放 | 继续按已有来源依赖保护拒绝冲销 |
| 撤销AUTO后等待任务轮询 | 同完成代次不重新创建释放 |
| 无权限、跨厂、旧版本、重复提交 | 原规则生效，失败无库存副作用 |

- [x] 将 `tools/verify-auto-release-positive-live.mjs` 的9组闭环重新全部跑通：正常完成、释放、B领用/还库、损耗归属、依赖阻断、撤销、合法重完成。
- [x] 在真实浏览器中验证冲销失败提示、刷新后库存与释放一致；处理依赖并撤销释放后验证合法冲销。失败不展示成功或把单据标为已冲销。
- [x] 只读扫描历史 ACTIVE 释放：可用件数/净重大于实物、释放余额负值、件数重量配对异常；同时检查有效释放涉及的已冲销调整单。输出具体单号及关系供人工核对，不自动修复历史。

## 任务5：回归、复审与交付

- [x] 后端先运行新增针对性测试，再运行完整回归。命令从 `backend` 执行：`mvn -o -pl ruoyi-metal -am test -Dtest=WarehouseAdjustmentReversalTest,WarehouseAdjustmentReversalConcurrencyTest,SemiFlowServiceTest,SemiReturnIntegrationTest,SemiAutoReleaseServiceTest,SemiAutoReleaseConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false`；随后 `mvn -o test`。真实数据库测试必须实际启用，缺少条件则明确列为跳过，不以mock通过替代。
- [x] 从仓库根运行 `node tools/reproduce-auto-release-adjustment-reversal.mjs`、`node tools/verify-auto-release-positive-live.mjs`；修复后两者均应退出0，保存接口与库存证据。
- [x] 前端执行 `npm run typecheck`、`npm run build:prod`，并运行现有仓储、半成品流程相关浏览器回归。统计实际通过、失败、跳过及原因。
- [x] 代码复审逐个检查冲销入口、校验顺序、整单回滚、锁顺序、AUTO/MANUAL一致性、批次逆向恢复及错误信息权限范围。
- [x] 验证后打包并重启本地后端，检查健康、调整冲销和释放查询关键接口；迁移不需要执行。
- [x] 清理本次隔离业务数据并只读复核，保留审计日志和测试证据。报告已完成/未完成、历史异常、测试数量与验证限制；不提交GitHub。

## 验收门槛

原复现变为明确业务拒绝且零副作用；合法半成品还库与批次冲销完整流程通过；真实并发测试不产生释放余额超过实物；历史异常仅报告、不改写。任一项未验证须作为未完成或限制披露，不能宣称全部修复。
