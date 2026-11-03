package com.ruoyi.metal.service;
import java.math.BigDecimal;import com.ruoyi.common.exception.ServiceException;
public final class OutboundDraftBalances {
 private OutboundDraftBalances(){}
 public static void requireCapacity(long available,long requested){if(requested>available)throw new ServiceException("外部接入共享额度不足：本工序可接入"+available+"件，全部仓库合计"+requested+"件");}
 public static void requireStock(BigDecimal available,long pieces,BigDecimal requested,long requestedPieces,boolean byWeight){if(requested.compareTo(available)>0||!byWeight&&requestedPieces>pieces)throw new ServiceException("全部明细合计超过库存可领余额");}
}
