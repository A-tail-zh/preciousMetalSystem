# Git版本管理

项目采用根目录单仓库，主分支main，统一跟踪backend、frontend、tools、docs及需求文档。原前后端若依上游仓库元数据保留在本机.runtime/git-history-backup/backend.git和frontend.git，不提交；源码来源见README。

.env、日志、缓存、依赖、构建产物和测试临时结果已忽略。.env.example可提交，真实密码只保存在本机。提交前检查git diff --cached。

日常操作：

```powershell
git status
git switch -c feature/功能名称
git diff
git add 本次修改的文件
git commit -m "说明本次修改"
git log --oneline -10
```

当前建立本地版本记录，未连接远程。提供自己的远程仓库地址后再设置：

```powershell
git remote add origin 你的仓库地址
git push -u origin main
```

回看旧版本使用git show <提交号>。回滚前先保存未提交的工作。不要强制添加.env，不向若依上游推送本项目修改。原始历史备份如需恢复，先保留根仓库及当前源码，再恢复对应元数据。
