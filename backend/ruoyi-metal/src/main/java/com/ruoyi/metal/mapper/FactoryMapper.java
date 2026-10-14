package com.ruoyi.metal.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.metal.domain.Factory;
import com.ruoyi.metal.domain.ProductionLine;
import com.ruoyi.metal.dto.FactoryRequest;

/**
 * 工厂数据访问接口，具体查询及写入规则由 MyBatis 映射文件定义。
 */
public interface FactoryMapper {
    /**
     * 按工厂访问范围查询有效工厂列表。
     */
    List<Factory> selectList(@Param("scopeId") Long scopeId);
    /**
     * 查询未删除的工厂记录。
     */
    Factory selectById(@Param("id") Long id);
    /**
     * 查询并锁定未删除的工厂记录。
     */
    Factory lockFactory(@Param("id") Long id);
    /**
     * 批量查询指定工厂的产线关联。
     */
    List<ProductionLine> selectLines(@Param("ids") List<Long> ids);
    /**
     * 查询未分配产线及当前工厂已有产线。
     */
    List<ProductionLine> selectOptions(@Param("factoryId") Long factoryId);
    /**
     * 锁定所选产线及当前工厂已有产线，保护关联变更。
     */
    List<ProductionLine> lockLines(@Param("ids") List<Long> ids, @Param("factoryId") Long factoryId);
    /**
     * 插入记录并回填数据库生成的主键。
     */
    int insert(@Param("form") FactoryRequest form, @Param("actor") String actor);
    /**
     * 根据请求版本号更新记录，返回受影响的行数。
     */
    int update(@Param("form") FactoryRequest form, @Param("actor") String actor);
    /**
     * 解除指定工厂的全部产线关联。
     */
    int clearLines(@Param("factoryId") Long factoryId);
    /**
     * 在产线未分配或属于当前工厂时更新归属，返回受影响的行数。
     */
    int assignLines(@Param("ids") List<Long> ids, @Param("factoryId") Long factoryId);
    /**
     * 统计指定工厂关联的产线数量。
     */
    int countLines(@Param("factoryId") Long factoryId);
    /**
     * 批量删除记录；业务层校验访问权限和引用限制。
     */
    int delete(@Param("id") Long id, @Param("actor") String actor);
}
