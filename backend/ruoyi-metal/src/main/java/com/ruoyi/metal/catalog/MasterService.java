package com.ruoyi.metal.catalog;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
/**
 * 基础资料的通用业务模板，统一执行访问检查、版本校验、审计字段维护和关联更新。
 *
 * @param <T> 基础资料记录类型
 */
public abstract class MasterService<T extends MasterRecord> {
    protected final MasterMapper<T> mapper;
    protected final FactoryScope scope;
    protected MasterService(MasterMapper<T> mapper,FactoryScope scope){this.mapper=mapper;this.scope=scope;}
    /**
     * 声明列表是否按照当前用户所属工厂过滤。
     */
    protected boolean scoped(){return false;}
    /**
     * 校验当前用户对目标记录所属工厂的访问权限。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void access(T record){}
    /**
     * 保存前校验业务数据，并准备需要持久化的字段。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void prepare(T record,T previous){}
    /**
     * 保存主记录的关联关系。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void relations(T record){}
    /**
     * 为查询结果批量补充关联资料。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void hydrate(List<T> records){}
    /**
     * 检查业务引用和保护规则，不满足删除条件时抛出异常。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void deletable(T record){}
    /**
     * 在锁定主记录前检查访问权限，并按固定顺序锁定涉及的工厂。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void beforeLock(T form){}
    /**
     * 批量删除前检查目标记录的访问权限，并锁定涉及的工厂。子类可覆盖此扩展点，默认不执行操作。
     */
    protected void beforeDelete(List<Long> ids){}
    /**
     * 查询列表并补充关联资料。
     */
    public List<T> list(){List<T> rows=mapper.list(scoped()?scope.currentFactory():null);hydrate(rows);return rows;}
    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    public List<T> list(ListQuery q){q.validate();Long factory=scoped()?scope.currentFactory():null;if(factory!=null&&q.getFactoryId()!=null&&!factory.equals(q.getFactoryId()))throw new ServiceException("不能查询其他工厂",403);List<T> rows=mapper.listFiltered(factory,q);hydrate(rows);return rows;}
    public T detail(Long id){T row=required(mapper.detail(id));access(row);hydrate(List.of(row));return row;}
    private T required(T value){if(value==null)throw new ServiceException("记录不存在或已删除");return value;}
    @Transactional(rollbackFor=Exception.class)
    /**
     * 在事务中校验新增参数、设置操作人、插入主记录并保存关联；重复键转换为业务异常。
     *
     * @param form 新增资料，不能携带已有 ID
     * @return 数据库生成的记录 ID 字符串
     */
    public String create(T form){
        if(form.getId()!=null)throw new ServiceException("新增不能指定ID");
        prepare(form,null);form.setCreateBy(SecurityUtils.getUsername());form.setUpdateBy(SecurityUtils.getUsername());
        try{mapper.insert(form);relations(form);}catch(DuplicateKeyException e){throw new ServiceException("编号、编码或名称已存在");}
        return form.getId().toString();
    }
    @Transactional(rollbackFor=Exception.class)
    /**
     * 在事务中锁定记录并核对版本，校验访问权限后更新主记录及关联关系。
     *
     * @param form 修改资料，必须携带 ID 和查询时的版本号
     */
    public void edit(T form){
        if(form.getId()==null || form.getVersion()==null)throw new ServiceException("修改必须提供ID和版本号");
        beforeLock(form);
        T previous=required(mapper.lock(form.getId()));access(previous);
        if(!previous.getVersion().equals(form.getVersion()))throw new ServiceException("数据已被修改，请刷新后重试");
        prepare(form,previous);form.setUpdateBy(SecurityUtils.getUsername());
        try{if(mapper.update(form)!=1)throw new ServiceException("数据已被修改，请刷新后重试");relations(form);}catch(DuplicateKeyException e){throw new ServiceException("编号、编码或名称已存在");}
    }
    @Transactional(rollbackFor=Exception.class)
    /**
     * 校验全部记录的访问权限和删除条件后统一删除，任一异常使事务回滚。
     *
     * @param ids 待删除 ID，允许重复，去重后按升序处理；最多传入 100 项
     */
    public void delete(Long[] ids){
        if(ids==null||ids.length==0||ids.length>100||Arrays.stream(ids).anyMatch(id->id==null||id<=0))throw new ServiceException("请选择1至100条有效记录");
        List<Long> sorted=Arrays.stream(ids).distinct().sorted().toList();beforeDelete(sorted);
        List<T> rows=sorted.stream().map(id->required(mapper.lock(id))).toList();
        rows.forEach(this::access);rows.forEach(this::deletable);
        rows.forEach(row->mapper.delete(row.getId(),SecurityUtils.getUsername()));
    }
}
