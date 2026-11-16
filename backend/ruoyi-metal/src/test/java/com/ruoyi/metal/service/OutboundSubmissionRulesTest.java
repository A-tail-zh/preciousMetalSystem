package com.ruoyi.metal.service;
import java.util.*;import java.time.LocalDate;import java.math.BigDecimal;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
class OutboundSubmissionRulesTest {
 private Line line(){return new Line(1L,null,null,null,null,BigDecimal.ONE,0L,null,null,null,null,null,null);}
 private Request request(List<Group> groups){return new Request(UUID.randomUUID().toString(),1L,"PRODUCTION_OUT",LocalDate.now(),List.of(),1L,1L,null,1L,1L,1L,null,null,groups);}
 @Test void operatorsAreOptional(){assertDoesNotThrow(()->OutboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())))),false));}
 @Test void rejectsNonProductionType(){var r=request(List.of(new Group(1L,null,List.of(line()))));assertThrows(ServiceException.class,()->OutboundSubmissionRules.validate(new Request(r.requestId(),1L,"SEMI_OUT",r.orderDate(),List.of(),1L,1L,null,1L,1L,1L,null,null,r.groups()),false));}
 @Test void rejectsDuplicateWarehouse(){assertThrows(ServiceException.class,()->OutboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())),new Group(1L,null,List.of(line())))),false));}
 @Test void emptyGroupAllowedOnlyForPreview(){var r=request(List.of(new Group(1L,null,List.of())));assertDoesNotThrow(()->OutboundSubmissionRules.validate(r,true));assertThrows(ServiceException.class,()->OutboundSubmissionRules.validate(r,false));}
 @Test void combinedLimit(){assertThrows(ServiceException.class,()->OutboundSubmissionRules.validate(request(List.of(new Group(1L,null,Collections.nCopies(600,line())),new Group(2L,null,Collections.nCopies(401,line())))),false));}
 @Test void independentKeeperRequired(){assertThrows(ServiceException.class,()->OutboundSubmissionRules.validate(request(List.of(new Group(1L,new People(List.of(),1L,null,null),List.of(line())))),false));}
 @Test void allWarehousesCanOverrideEmptyCommonPeople(){var r=request(List.of(new Group(1L,new People(List.of(),1L,1L,null),List.of(line()))));assertDoesNotThrow(()->OutboundSubmissionRules.validate(new Request(r.requestId(),1L,r.type(),r.orderDate(),List.of(),null,null,null,1L,1L,1L,null,null,r.groups()),false));}
 @Test void childRequestsCannotCollideWithInbound(){String id=UUID.randomUUID().toString();assertNotEquals(InboundSubmissionRules.childRequest(id,1L),OutboundSubmissionRules.childRequest(id,1L));}
 @Test void sharedCapacityCountsEveryWarehouse(){assertThrows(ServiceException.class,()->OutboundDraftBalances.requireCapacity(5,6));}
}
