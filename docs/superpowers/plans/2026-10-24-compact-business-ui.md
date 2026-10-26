# 简约紧凑业务页面 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 减少空白和无效滚动，统一业务页面视觉与操作位置，保留现有业务、权限和接口。

**Architecture:** 修改现有组件的布局源规则，提取少量共享设计变量；不靠全局!important覆盖所有Element组件。移动端维持44px主要触控区域，表格在内部滚动。

**Tech Stack:** Vue3、Element Plus、SCSS、Playwright。

## 默认设计方向

这是对原始美化请求采用的推荐方向，非用户额外确认的偏好。蓝#246fb4、正文#25364a、次要文字#67788a、边线#e2e8f0、页面底#f5f7fa、白色内容区。字体使用系统中文无衬线。页头56px、侧栏220px、页面16px边距，8/12/16px间距体系。列表分页随内容；明细保持完整可查；不加装饰渐变或营销元素。

## 步骤

- [x] 1. 增加列表/页头密度浏览器验收，确认旧布局不满足。
- [x] 2. 调整FactoryLayout/FactoryNavigation，保持移动菜单和权限行为。
- [x] 3. 统一BusinessFilters和业务列表的边距、分页、表头，优化工厂列宽。
- [x] 4. 去掉弹窗及工单卡片不必要的最小高度，收紧信息间距，报价/日志布局同步。
- [x] 5. 四视口截图检查、相关前端回归、类型检查和构建。记录效果及限制。

本轮不修改业务数据或接口，不自动提交GitHub。
