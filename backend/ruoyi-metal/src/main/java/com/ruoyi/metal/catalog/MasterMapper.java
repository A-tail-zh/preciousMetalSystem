package com.ruoyi.metal.catalog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
/**
 * 基础资料持久化接口，由具体业务 Mapper 和 MyBatis SQL 实现查询及写入。
 *
 * @param <T> 基础资料记录类型
 */
public interface MasterMapper<T extends MasterRecord> {
    /**
     * 查询列表并补充关联资料。
     */
    List<T> list(@Param("factoryId") Long factoryId);
    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    List<T> listFiltered(@Param("factoryId") Long factoryId,@Param("q") ListQuery q);
    T detail(@Param("id") Long id);
    /**
     * 查询并锁定记录，供当前事务执行并发校验。
     */
    T lock(@Param("id") Long id);
    /**
     * 插入记录并回填数据库生成的主键。
     */
    int insert(T form);
    /**
     * 根据请求版本号更新记录，返回受影响的行数。
     */
    int update(T form);
    /**
     * 批量删除记录；业务层校验访问权限和引用限制。
     */
    int delete(@Param("id") Long id,@Param("actor") String actor);
}
