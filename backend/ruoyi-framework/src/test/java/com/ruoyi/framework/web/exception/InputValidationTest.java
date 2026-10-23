package com.ruoyi.framework.web.exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.metal.catalog.WarehouseRequests;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import static org.junit.jupiter.api.Assertions.*;
class InputValidationTest {

 @Test void rejectsFractionalPieceBeforeJacksonCanTruncate()throws Exception {assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"items\":[{\"quantity\":1.5}]}"),WorkOrderRequest.class).isEmpty());}
 @Test void rejectsExponentsInNumbersAndStrings()throws Exception {for(String n:new String[]{"1e2","\"1e2\""})assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"lines\":[{\"netWeight\":"+n+"}]}"),WarehouseRequests.Order.class).isEmpty());}
 @Test void keepsExactDecimalAndStringIds()throws Exception {assertTrue(InputPayloadValidation.check(InputPayloadValidation.parse("{\"warehouseId\":\"9007199254740993\",\"lines\":[{\"netWeight\":\"99999999999999.123456\",\"quantity\":2}]}"),WarehouseRequests.Order.class).isEmpty());}
 @Test void nestedErrorsHaveOriginalPaths()throws Exception {var e=InputPayloadValidation.check(InputPayloadValidation.parse("{\"items\":[{\"quantity\":\"3.1\"}]}"),WorkOrderRequest.class);assertEquals("items[0].quantity",e.get(0).get("field"));}
 @Test void rejectsIntegerOverflowAndBooleans()throws Exception {for(String n:new String[]{"2147483648","true","\"NaN\""})assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"items\":[{\"quantity\":"+n+"}]}"),WorkOrderRequest.class).isEmpty());}
 @Test void optionalNullIsNotChanged()throws Exception {assertTrue(InputPayloadValidation.check(InputPayloadValidation.parse("{\"completedQuantity\":null,\"lines\":[{\"purity\":null}]}"),WarehouseRequests.Order.class).isEmpty());}
 @Test void errorsAreChineseAndDoNotContainRejectedValues(){var b=new BeanPropertyBindingResult(new Object(),"order");b.rejectValue("", "NotBlank", "must not be blank");var response=InputValidationErrors.from(b);assertNotNull(response.get("fieldErrors"));assertFalse(response.get("msg").toString().contains("must"));}
 @Test void fractionalArrayIntegerIsRejected()throws Exception {assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("[{\"quantity\":1.25}]"),WarehouseRequests.Line[].class).isEmpty());}
 @Test void invalidDatesCannotBeCoerced()throws Exception {for(String value:new String[]{"2026-02-30","2026-13-01","2026-1-1"})assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"orderDate\":\""+value+"\"}"),WorkOrderRequest.class).isEmpty());}
 @Test void exactDatesAndEmptyOptionalFieldsRemainValid()throws Exception {assertTrue(InputPayloadValidation.check(InputPayloadValidation.parse("{\"orderDate\":\"2026-02-28\",\"items\":[{\"plans\":[{\"startTime\":\"2026-02-28 12:30:00\"}]}]}"),WorkOrderRequest.class).isEmpty());}
 @Test void queryErrorsDoNotEchoInternalTypesOrValues(){var e=new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException("private-value",Long.class,"factoryId",null,new IllegalArgumentException());var result=new GlobalExceptionHandler().handleMethodArgumentTypeMismatchException(e,org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class));assertFalse(result.get("msg").toString().contains("private-value"));assertFalse(result.get("msg").toString().contains("java.lang"));}
 @Test void allScientificNumericTokensAreRejectedBeforeNormalization()throws Exception {for(String token:new String[]{"1e0","1e-1","1.0e-2","1E+2"}){assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"lines\":[{\"netWeight\":"+token+"}]}"),WarehouseRequests.Order.class).isEmpty());assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"dimensions\":{\"diameter\":"+token+"}}"),com.ruoyi.metal.catalog.CalculationRequest.class).isEmpty());}}
 @Test void nullPrimitiveDoesNotSilentlyBecomeZero()throws Exception {assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"plannedQuantity\":null}"),WarehouseRequests.Batch.class).isEmpty());}
 public record Toggle(Boolean enabled){}
 @Test void numericBooleanDoesNotSilentlyBecomeTrue()throws Exception {assertFalse(InputPayloadValidation.check(InputPayloadValidation.parse("{\"enabled\":1}"),Toggle.class).isEmpty());assertTrue(InputPayloadValidation.check(InputPayloadValidation.parse("{\"enabled\":true}"),Toggle.class).isEmpty());}
}
