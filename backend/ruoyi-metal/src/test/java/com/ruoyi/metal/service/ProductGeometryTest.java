package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductGeometryTest {
    static final Map<String,String> FLAT=Map.of("innerDiameter","2","outerDiameter","4","wallThickness","0.5","innerHeight","3","bottomThickness","1");
    static final Map<String,String> TAPER=Map.of("upperInnerDiameter","4","upperOuterDiameter","6","wallThickness","0.5","longInnerHeight","3","lowerInnerDiameter","2","angle","0","bottomThickness","1");
    static BigDecimal volume(String type,Map<String,String> d){return ProductGeometry.volume(type,ProductGeometry.normalize(type,d),new BigDecimal("3.14"),new BigDecimal("1.03"));}
    @Test void eightFormulasMatchIndependentHandCalculatedExamples(){
        assertEquals("21.20",volume("平底坩埚",FLAT).toPlainString());
        assertEquals("9.42",volume("环",Map.of("outerDiameter","4","innerDiameter","2","thickness","1")).toPlainString());
        assertEquals("56.52",volume("杆",Map.of("innerDiameter","2","outerDiameter","4","length","3")).toPlainString());
        assertEquals("52.60",volume("梯形",TAPER).toPlainString());
        assertEquals("21.83",volume("弧底",FLAT).toPlainString());
        assertEquals("7.07",volume("靶",Map.of("innerDiameter","2","wallThickness","0.5","bottomThickness","1")).toPlainString());
        assertEquals("52.00",volume("方形坩埚",Map.of("length","5","width","4","height","3","innerLength","2","innerWidth","2","innerHeight","2")).toPlainString());
        assertEquals("60.00",volume("方形盖",Map.of("length","5","width","4","height","3")).toPlainString());
    }
    @Test void solidRodUsesConfirmedBusinessFormula(){assertEquals("6.28",volume("杆",Map.of("innerDiameter","0","outerDiameter","2","length","1")).toPlainString());}
    @Test void sixtyDegreesKeepsHalfUpBoundaryAndNearNinetyIsFinite(){
        var d=new HashMap<>(TAPER);d.put("angle","60");assertEquals("62.02",volume("梯形",d).toPlainString());
        d.put("angle","89.99");assertTrue(volume("梯形",d).signum()>0);
        d.put("lowerInnerDiameter","4");assertEquals("43.18",volume("梯形",d).toPlainString());
    }
    @Test void invalidAndRoundedBoundaryInputsReject(){
        for(String angle:List.of("-1","90","89.999","180","1e1","+1","NaN","1.1234567")){
            var d=new HashMap<>(TAPER);d.put("angle",angle);assertThrows(RuntimeException.class,()->volume("梯形",d),angle);
        }
        var d=new HashMap<>(TAPER);d.put("lowerInnerDiameter","5");assertThrows(RuntimeException.class,()->volume("梯形",d));
        var flat=new HashMap<>(FLAT);flat.put("wallThickness","0.004");assertThrows(RuntimeException.class,()->volume("平底坩埚",flat));
        flat.put("wallThickness","-0.004");assertThrows(RuntimeException.class,()->volume("平底坩埚",flat));
        assertThrows(RuntimeException.class,()->volume("环",Map.of("outerDiameter","2","innerDiameter","2","thickness","1")));
        assertThrows(RuntimeException.class,()->volume("方形坩埚",Map.of("length","2","width","2","height","2","innerLength","2","innerWidth","1","innerHeight","1")));
    }
    @Test void exactFieldsAndOverflowAreValidated(){
        assertThrows(RuntimeException.class,()->volume("平底坩埚",Map.of("innerDiameter","2")));
        var d=new HashMap<>(FLAT);d.put("surprise","1");assertThrows(RuntimeException.class,()->volume("平底坩埚",d));
        assertThrows(RuntimeException.class,()->volume("方形盖",Map.of("length","99999999999999","width","2","height","2")));
    }
    @Test void factorIsAppliedBeforeVolumeRoundingAndWeightUsesRoundedVolume(){
        assertEquals("21.83",volume("弧底",FLAT).toPlainString());
        assertEquals(new BigDecimal("42.40"),ProductGeometry.weight(volume("平底坩埚",FLAT),"2"));
    }
    @Test void missingParametersStayMissingAndSpecificationKeepsAngleUnit(){
        assertNull(ProductGeometry.volume("弧底",ProductGeometry.normalize("弧底",FLAT),new BigDecimal("3.14"),null));
        assertNull(ProductGeometry.weight(BigDecimal.ONE,null));
        assertTrue(ProductGeometry.specification("梯形",ProductGeometry.normalize("梯形",TAPER)).contains("夹角0°"));
    }
}
