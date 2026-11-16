package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.*;

/** Called after replay checks: request fingerprints always describe the original payload. */
public final class WarehousePrecision {
    private WarehousePrecision() {}
    public static Line line(Line x) { return line(x,false); }
    private static Line line(Line x,boolean signed) {
        if(!signed && (x.netWeight()!=null&&x.netWeight().signum()<0 || x.grossWeight()!=null&&x.grossWeight().signum()<0 || x.impurity()!=null&&x.impurity().signum()<0))throw new ServiceException("重量不能小于0");
        if(!signed && x.grossWeight()!=null && x.impurity()!=null && x.impurity().compareTo(x.grossWeight())>0)throw new ServiceException("杂质不能大于毛重");
        BigDecimal gross=BusinessDecimal.round(x.grossWeight()), impurity=BusinessDecimal.round(x.impurity()),net=BusinessDecimal.round(x.netWeight());
        if(gross!=null && net!=null && gross.subtract(impurity==null?BigDecimal.ZERO:impurity).compareTo(net)!=0)
            throw new ServiceException("保留两位小数后，净重须等于毛重减杂质");
        return new Line(x.inventoryId(),x.sourceLineId(),x.category(),x.referenceId(),x.batchNo(),net,x.quantity(),gross,impurity,BusinessDecimal.round(x.purity()),x.organization(),x.purpose(),x.remark(),x.receivingOrganization(),x.releaseId(),x.skipPermitId(),x.skipPermitVersion(),x.referenceQuantity(),x.customerId(),x.customerVersion(),x.materialOwnership(),x.powderOrigin());
    }
    public static List<Line> lines(List<Line> lines){return lines.stream().map(WarehousePrecision::line).toList();}
    public static Order order(Order x){return new Order(x.requestId(),x.factoryId(),x.warehouseId(),x.type(),x.orderDate(),x.operatorId(),x.operatorIds(),x.keeperId(),x.recipientId(),x.inspectorId(),x.reviewerId(),x.workOrderId(),x.batchId(),x.batchVersion(),x.settle(),x.completedQuantity(),x.reason(),x.lines().stream().map(l->line(l,"ADJUST".equals(x.type()))).toList(),x.itemId(),x.processId());}
    public static Adjustment adjustment(Adjustment x){return new Adjustment(x.requestId(),x.version(),x.operatorId(),x.keeperId(),BusinessDecimal.round(x.deltaWeight()),x.deltaQuantity(),x.reason(),BusinessDecimal.round(x.deltaGrossWeight()),BusinessDecimal.round(x.deltaImpurity()),x.referenceQuantity());}
}
