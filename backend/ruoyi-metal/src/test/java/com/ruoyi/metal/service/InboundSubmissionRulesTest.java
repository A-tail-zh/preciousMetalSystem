package com.ruoyi.metal.service;
import java.util.*;import java.math.BigDecimal;import java.time.LocalDate;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.InboundSubmissionRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
class InboundSubmissionRulesTest {
 private Line line(){return new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,null,null,null);}
 private Request request(List<Group> groups){return new Request(UUID.randomUUID().toString(),1L,"RAW_IN",LocalDate.now(),List.of(1L),1L,null,null,null,null,null,null,null,false,null,groups);}
 @Test void acceptsTwoDifferentWarehouses(){assertDoesNotThrow(()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())),new Group(2L,null,List.of(line()))))));}
 @Test void rejectsRepeatedWarehouse(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())),new Group(1L,null,List.of(line()))))));}
 @Test void rejectsEmptyGroup(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of())))));}
 @Test void totalLimitIncludesAllGroups(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,Collections.nCopies(600,line())),new Group(2L,null,Collections.nCopies(401,line()))))));}
 @Test void childRequestIsStablePerWarehouse(){var root=UUID.randomUUID().toString();assertEquals(InboundSubmissionRules.childRequest(root,1L),InboundSubmissionRules.childRequest(root,1L));assertNotEquals(InboundSubmissionRules.childRequest(root,1L),InboundSubmissionRules.childRequest(root,2L));}
 @Test void outputActionExpressionSeparatesLegacyAndGroupedIds(){var sql=BatchOutputQueries.actionKey("o");assertTrue(sql.contains("inbound_submission_id"));assertTrue(sql.contains("D:"));assertTrue(sql.contains("G:"));}
}
