package com.ruoyi.framework.web.exception;
import jakarta.validation.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import com.ruoyi.system.domain.*;
class SystemInputCapacityTest {
 private static ValidatorFactory factory;private static Validator validator;
 @BeforeAll static void init(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}
 @AfterAll static void close(){factory.close();}
 @Test void configRemarksStopBeforeSqlAt501Characters(){var value=new SysConfig();value.setRemark("测".repeat(501));assertFalse(validator.validateProperty(value,"remark").isEmpty());value.setRemark("测".repeat(500));assertTrue(validator.validateProperty(value,"remark").isEmpty());}
 @Test void configCapacitiesMatchTheirOwnColumns(){var value=new SysConfig();value.setConfigName("a".repeat(101));value.setConfigKey("a".repeat(101));value.setConfigValue("a".repeat(501));for(String property:new String[]{"configName","configKey","configValue"})assertFalse(validator.validateProperty(value,property).isEmpty());}
 @Test void noticeRemarkUses255InsteadOfGeneric500(){var value=new SysNotice();value.setRemark("a".repeat(256));assertFalse(validator.validateProperty(value,"remark").isEmpty());value.setRemark("a".repeat(255));assertTrue(validator.validateProperty(value,"remark").isEmpty());}
 @Test void invalidConfigurationTypeIsRejected(){var value=new SysConfig();value.setConfigType("X");assertFalse(validator.validateProperty(value,"configType").isEmpty());value.setConfigType("Y");assertTrue(validator.validateProperty(value,"configType").isEmpty());}
}
