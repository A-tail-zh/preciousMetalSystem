package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BusinessDecimalTest {
 @Test void exactHalfUpAndNull(){
  for(var pair:List.of(new String[]{"1.234","1.23"},new String[]{"1.235","1.24"},new String[]{"-1.235","-1.24"},new String[]{"0.005","0.01"},new String[]{"-0.004","0.00"}))assertEquals(pair[1],BusinessDecimal.display(new BigDecimal(pair[0])));
  assertNull(BusinessDecimal.round(null));assertEquals("",BusinessDecimal.display(null));
 }
 @Test void roundedZeroAndOverflowCannotBecomeBusinessValues(){
  assertThrows(RuntimeException.class,()->BusinessDecimal.positive(new BigDecimal("0.004"),"净重"));
  assertThrows(RuntimeException.class,()->BusinessDecimal.round(new BigDecimal("99999999999999.995")));
 }
 @Test void unchangedLegacyValueRetainsPrecision(){var old=new BigDecimal("1.234567");assertSame(old,BusinessDecimal.changed(new BigDecimal("1.234567"),old));assertEquals(new BigDecimal("1.24"),BusinessDecimal.changed(new BigDecimal("1.235"),old));}
 @Test void incompatibleLegacyBalancesBlockNewWrites(){assertThrows(RuntimeException.class,()->BusinessDecimal.requireCompatibleBalance(new BigDecimal("1.000001"),"库存"));assertDoesNotThrow(()->BusinessDecimal.requireCompatibleBalance(new BigDecimal("1.230000"),"库存"));}
 @Test void allocationConservesCentsAndHasNoNegativeTail(){assertEquals(List.of(new BigDecimal("0.01"),new BigDecimal("0.00"),new BigDecimal("0.00")),LossCalculator.allocate(new BigDecimal("0.01"),List.of(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE)));}
 @Test void originalComponentRangeMustNotBeHiddenByRounding(){
  var line=new com.ruoyi.metal.catalog.WarehouseRequests.Line(null,null,"RAW",1L,null,BigDecimal.ZERO,0L,new BigDecimal("1.235"),new BigDecimal("1.236"),null,null,null,null);
  assertThrows(RuntimeException.class,()->WarehousePrecision.line(line));
 }
}
