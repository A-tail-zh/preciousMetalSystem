package com.ruoyi.metal.security;

import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.core.domain.model.LoginUser;

/**
 * Central scope and grant checks. Never trust a client-supplied scope.
 */
@Component("metalScope")
public class FactoryScope {
    private final JdbcTemplate jdbc;
    private final RedisCache cache;

    public FactoryScope(JdbcTemplate jdbc, RedisCache cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    /**
     * 判断当前登录用户是否为系统超级管理员。
     *
     * @return 是否为系统超级管理员
     */
    public boolean isAdmin() {
        return SecurityUtils.isAdmin(SecurityUtils.getUserId());
    }

    /**
     * 限制后续操作仅允许系统超级管理员执行。
     *
     * @throws ServiceException 当前用户不是系统超级管理员
     */
    public void adminOnly() {
        if (!isAdmin()) deny();
    }

    /**
     * 检查账号未删除且启用，并确认普通账号所属工厂有效；超级管理员无需绑定工厂。
     *
     * @param userId 待检查的用户 ID
     * @return 账号及其工厂关系是否有效
     */
    public boolean activeAccount(Long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u LEFT JOIN metal_factory f ON f.factory_id=u.factory_id AND f.del_flag='0' WHERE u.user_id=? AND u.status='0' AND u.del_flag='0' AND (u.user_id=1 OR f.factory_id IS NOT NULL)", Integer.class, userId) > 0;
    }

    /**
     * 从数据库获取当前账号所属的有效工厂，不信任客户端传入的工厂范围。
     *
     * @return 普通用户的工厂 ID；超级管理员返回 null，表示不限定工厂
     * @throws ServiceException 普通账号未配置有效工厂
     */
    public boolean activeSession(Long userId,long authVersion){return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u LEFT JOIN metal_factory f ON f.factory_id=u.factory_id AND f.del_flag='0' WHERE u.user_id=? AND u.auth_version=? AND u.status='0' AND u.del_flag='0' AND (u.user_id=1 OR f.factory_id IS NOT NULL)",Integer.class,userId,authVersion)>0;}
    public Long currentFactory() {
        if (isAdmin()) return null;
        List<Long> ids = jdbc.query("SELECT u.factory_id FROM sys_user u JOIN metal_factory f ON f.factory_id=u.factory_id AND f.del_flag='0' WHERE u.user_id=? AND u.del_flag='0' AND u.status='0'", (r, n) -> r.getLong(1), SecurityUtils.getUserId());
        if (ids.isEmpty()) throw new ServiceException("请先配置有效的所属工厂", 403);
        return ids.get(0);
    }

    /**
     * 检查目标工厂是否存在、未删除，且当前用户有权访问。
     *
     * @param factoryId 目标工厂 ID
     * @throws ServiceException 工厂无效或当前用户无权跨工厂访问
     */
    public void check(Long factoryId) {
        Long current = currentFactory();
        if (current != null && !current.equals(factoryId)) deny();
        if (factoryId == null || jdbc.queryForObject("SELECT COUNT(*) FROM metal_factory WHERE factory_id=? AND del_flag='0'", Integer.class, factoryId) == 0)
            throw new ServiceException("所属工厂不存在或已删除");
    }

    /**
     * 校验工厂访问权限，并锁定有效的工厂记录，保护当前事务中的后续业务操作。
     *
     * <p>先过滤空 ID、去重，再按 ID 升序逐个加锁，减少重复查询和加锁顺序不一致
     * 导致的死锁风险。每个 ID 先通过 {@link #check(Long)} 校验访问权限，随后使用
     * {@code SELECT ... FOR UPDATE} 再次确认工厂未删除，并对匹配记录加排他锁。
     *
     * <p>调用方应在事务中调用本方法，锁在事务提交或回滚后释放。未开启事务且连接
     * 使用自动提交时，查询结束后锁即释放，无法保护后续业务操作。
     * 空集合或仅包含空 ID 的集合不会执行查询。
     *
     * @param ids 工厂 ID 集合，集合本身不能为 null；其中的 null 元素会被忽略
     * @throws ServiceException 当前用户无权访问目标工厂，或工厂不存在、已删除
     * @throws NullPointerException 工厂 ID 集合为 null
     */
    public void lockFactories(Collection<Long> ids) {
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().sorted().toList()) {
            check(id);
            if (jdbc.queryForList("SELECT factory_id FROM metal_factory WHERE factory_id=? AND del_flag='0' FOR UPDATE", id).isEmpty())
                throw new ServiceException("工厂不存在或已删除");
        }
    }

    /**
     * 检查菜单有效性及授权范围，阻止普通用户授予自身没有的权限。
     *
     * @param menuIds 待授权的菜单 ID 列表，不允许无效或重复 ID
     * @throws ServiceException 菜单无效或授权超出当前用户权限
     */
    public void menuGrants(List<Long> menuIds) {
        if (menuIds == null || menuIds.size() > 10000 || menuIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(menuIds).size() != menuIds.size())
            throw new ServiceException("权限列表无效");
        Set<String> owned = SecurityUtils.getLoginUser().getPermissions();
        for (Long id : menuIds.stream().sorted().toList()) {
            List<String> permissions = jdbc.query("SELECT perms FROM sys_menu WHERE menu_id=? AND status='0'", (r, n) -> r.getString(1), id);
            if (permissions.isEmpty()) throw new ServiceException("菜单不存在或已停用");
            String permission = permissions.get(0);
            if (!isAdmin() && permission != null && !permission.isBlank() && !owned.contains(permission) && !owned.contains("*:*:*"))
                deny();
        }
    }

    /**
     * 检查角色启用状态、所属工厂及权限范围，禁止分配系统超级管理员角色。
     *
     * @param factoryId 接收角色的工作人员所属工厂
     * @param roleIds 待分配的角色 ID 列表，不能为空或包含重复 ID
     * @throws ServiceException 角色无效、跨工厂或包含当前用户不能授予的权限
     */
    public void roleGrants(Long factoryId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty() || roleIds.size() > 1000 || roleIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(roleIds).size() != roleIds.size())
            throw new ServiceException("请选择有效角色，角色不能重复");
        for (Long id : roleIds.stream().sorted().toList()) {
            if (id == 1L) throw new ServiceException("不能分配系统超级管理员角色", 403);
            List<Map<String, Object>> roles = jdbc.queryForList("SELECT factory_id FROM sys_role WHERE role_id=? AND del_flag='0' AND status='0' FOR UPDATE", id);
            if (roles.isEmpty() || roles.get(0).get("factory_id") == null || ((Number) roles.get(0).get("factory_id")).longValue() != factoryId)
                throw new ServiceException("角色必须属于所选工厂且有效", 403);
            menuGrants(jdbc.query("SELECT menu_id FROM sys_role_menu WHERE role_id=? ORDER BY menu_id", (r, n) -> r.getLong(1), id));
        }
    }

    /**
     * 删除指定用户的全部 Redis 登录缓存，使旧登录在后续请求中失效。
     *
     * <p>事务同步已开启时注册提交后回调，事务回滚不注销登录；否则立即执行。
     * 本方法不会修改账号状态或角色权限，也不会主动关闭客户端页面。
     *
     * @param userIds 需要注销登录的用户 ID 集合，集合本身不能为 null
     */
    public void invalidate(Collection<Long> userIds) {
        Set<Long> affected = new HashSet<>(userIds);
        for(Long id:affected.stream().sorted().toList())jdbc.update("UPDATE sys_user SET auth_version=auth_version+1 WHERE user_id=?",id);
        Runnable action = () -> {
            try { for (String key : cache.keys("login_tokens:*")) {
                LoginUser login = cache.getCacheObject(key);
                if (login != null && affected.contains(login.getUserId())) cache.deleteObject(key);
            }}catch(Exception failure){org.slf4j.LoggerFactory.getLogger(FactoryScope.class).warn("SESSION_CACHE_INVALIDATION_FAILED {}",failure.getClass().getSimpleName());}
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else action.run();
    }

    /**
     * 统一拒绝跨工厂访问或越权授权，抛出业务码为 403 的异常。
     */
    public static void deny() {
        throw new ServiceException("无权访问其他工厂或授予更高权限", 403);
    }
}
