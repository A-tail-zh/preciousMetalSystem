package com.ruoyi.metal.service;
import java.math.BigDecimal;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.ruoyi.common.exception.ServiceException;
class RawReturnAllowanceTest {
 private BigDecimal n(String v){return new BigDecimal(v);}
 @Test void batchExcludesOutputAndPriorReturns(){assertEquals(n("90.000000"),RawReturnAllowance.remaining("300","190","20"));}
 @Test void sourceAndBatchBothLimit(){assertEquals(n("20.000000"),RawReturnAllowance.limit(n("20"),n("90"),"OPEN"));assertEquals(n("90.000000"),RawReturnAllowance.limit(n("280"),n("90"),"OPEN"));}
 @Test void closedAndNegativeAreZero(){for(String status:new String[]{"SETTLED","VOID",""})assertEquals(n("0.000000"),RawReturnAllowance.limit(n("300"),n("90"),status));assertEquals(n("0.000000"),RawReturnAllowance.limit(n("300"),n("-1"),"OPEN"));}
 @Test void sharedBudgetRejectsSecondSourceOverdraw(){RawReturnAllowance.require(n("100"),n("90"),n("60"),"OPEN");assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("200"),n("30"),n("31"),"OPEN"));}
 @Test void sixDecimalsAreExact(){assertEquals(n("0.000001"),RawReturnAllowance.remaining("300.000003","300.000001","0.000001"));}
 @Test void rejectsHistoricalAndClosed(){assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("300"),n("-1"),n("1"),"OPEN"));assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("300"),n("90"),n("1"),"SETTLED"));}
 @Test void zeroReturnIsValidAndNeverConsumesAllowance(){assertDoesNotThrow(()->RawReturnAllowance.require(n("280"),n("90"),n("0"),"OPEN"));assertDoesNotThrow(()->RawReturnAllowance.require(n("280"),n("90"),n("0.000000"),"OPEN"));assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("280"),n("90"),n("-0.000001"),"OPEN"));}
 @Test void zeroDoesNotBypassClosedOrHistoricalState(){assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("280"),n("90"),n("0"),"SETTLED"));assertThrows(ServiceException.class,()->RawReturnAllowance.require(n("280"),n("-1"),n("0"),"OPEN"));}
}
