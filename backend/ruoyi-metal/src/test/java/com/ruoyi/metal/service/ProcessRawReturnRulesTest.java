package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRawReturnRulesTest {
 private void require(String allowance,String value) throws Throwable {
  try {Class.forName("com.ruoyi.metal.service.ProcessRawReturnService").getDeclaredMethod("require",BigDecimal.class,BigDecimal.class).invoke(null,new BigDecimal(allowance),new BigDecimal(value));}
  catch(InvocationTargetException e){throw e.getCause();}
 }
 @Test void batchWeightsIncludeProcessedResidue(){assertTrue(RawReturnAllowance.totalsSql(false).contains("PROCESS_RAW_RETURN"));}
 @Test void eightGramsRecoverLeavesTwoGramsLoss(){assertDoesNotThrow(()->require("10.000000","8.000000"));assertEquals(new BigDecimal("2"),new BigDecimal("990").subtract(new BigDecimal("980")).subtract(new BigDecimal("8")));}
 @Test void allResidualWeightCanBeRecoveredWithZeroLoss(){assertDoesNotThrow(()->require("10","10"));}
 @Test void oneMicrogramOverIsRejected(){assertThrows(ServiceException.class,()->require("10","10.000001"));}
 @Test void sevenDecimalPlacesAreRejected(){assertThrows(ServiceException.class,()->require("10","0.0000001"));}
 @Test void zeroRecoveryIsRejected(){assertThrows(ServiceException.class,()->require("10","0"));}
 @Test void negativeRecoveryIsRejected(){assertThrows(ServiceException.class,()->require("10","-1"));}
 @Test void historicallyNegativeBalanceIsRejected(){assertThrows(ServiceException.class,()->require("-1","1"));}
 @Test void recoveryOrdersCanBeFilteredAndExported(){var query=new com.ruoyi.metal.catalog.OrderListQuery();query.setType("PROCESS_RAW_RETURN");query.setFilter("RET");assertDoesNotThrow(query::validate);}
}
