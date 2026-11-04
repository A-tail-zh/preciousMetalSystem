package com.ruoyi.metal.catalog;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;

public abstract class FactoryCatalogService<T extends FactoryCatalogRecord> extends MasterService<T> {
    protected FactoryCatalogService(MasterMapper<T> mapper,FactoryScope scope){super(mapper,scope);}
    @Override protected boolean scoped(){return true;}
    @Override protected void access(T record){
        if(record.getFactoryId()==null)throw new ServiceException("历史公共资料仅供历史查询，请创建本厂资料",403);
        scope.check(record.getFactoryId());
    }
    @Override protected void prepare(T record,T previous){
        Long factory=scope.resolveFactory(record.getFactoryId());
        if(previous!=null&&!Objects.equals(factory,previous.getFactoryId()))throw new ServiceException("基础资料不能转移工厂",403);
        record.setFactoryId(factory);
        scope.lockFactories(Collections.singletonList(factory));
    }
    @Override protected void beforeLock(T form){
        T previous=mapper.detail(form.getId());
        if(previous!=null){access(previous);scope.lockFactories(List.of(previous.getFactoryId()));}
    }
    @Override protected void beforeDelete(List<Long> ids){
        List<Long> factories=new ArrayList<>();
        for(Long id:ids){T row=mapper.detail(id);if(row!=null){access(row);factories.add(row.getFactoryId());}}
        scope.lockFactories(factories);
    }
}
