package com.ruoyi.metal.service;

import java.math.*;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** Version 2 geometry shared by product saves, previews and weight trials. */
public final class ProductGeometry {
    private ProductGeometry() {}
    public static final Map<String,List<String>> FIELDS=Map.of(
        "平底坩埚",List.of("innerDiameter","outerDiameter","wallThickness","innerHeight","bottomThickness"),
        "环",List.of("outerDiameter","innerDiameter","thickness"),
        "杆",List.of("innerDiameter","outerDiameter","length"),
        "梯形",List.of("upperInnerDiameter","upperOuterDiameter","wallThickness","longInnerHeight","lowerInnerDiameter","angle","bottomThickness"),
        "弧底",List.of("innerDiameter","outerDiameter","wallThickness","innerHeight","bottomThickness"),
        "靶",List.of("innerDiameter","wallThickness","bottomThickness"),
        "方形坩埚",List.of("length","width","height","innerLength","innerWidth","innerHeight"),
        "方形盖",List.of("length","width","height"));
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("innerDiameter","内径"),Map.entry("outerDiameter","外径"),Map.entry("wallThickness","壁厚"),
        Map.entry("innerHeight","内高"),Map.entry("bottomThickness","底厚"),Map.entry("thickness","厚"),
        Map.entry("length","长"),Map.entry("width","宽"),Map.entry("height","高"),Map.entry("innerLength","内长"),Map.entry("innerWidth","内宽"),
        Map.entry("upperInnerDiameter","上内径"),Map.entry("upperOuterDiameter","上外径"),Map.entry("longInnerHeight","长内高"),
        Map.entry("lowerInnerDiameter","下内径"),Map.entry("angle","夹角"));
    public static String label(String type,String key) {return "杆".equals(type)&&"length".equals(key)?"长度":LABELS.getOrDefault(key,key);}
    public static boolean zeroAllowed(String type,String key) {return "angle".equals(key)||"杆".equals(type)&&"innerDiameter".equals(key);}
    public static Map<String,String> normalize(String type,Map<String,String> input) {
        ProductTypes.require(type);
        var fields=FIELDS.get(type);
        if(input==null||!input.keySet().equals(new HashSet<>(fields)))throw new ServiceException("请填写产品类型对应的全部字段，不得保留其他类型尺寸");
        Map<String,String> result=new LinkedHashMap<>();
        for(String key:fields){
            String text=input.get(key),name=label(type,key);
            if(text==null||!text.matches("[0-9]+(\\.[0-9]{1,6})?"))throw new ServiceException(name+"须为合法十进制数字，最多6位小数，不支持科学计数法");
            BigDecimal value=new BigDecimal(text);
            if(value.precision()-value.scale()>14)throw new ServiceException(name+"最多14位整数");
            value=zeroAllowed(type,key)?BusinessDecimal.round(value):BusinessDecimal.positive(value,name);
            if("angle".equals(key)&&value.compareTo(new BigDecimal("90"))>=0)throw new ServiceException("夹角须为0°至不足90°，舍入后也须小于90°");
            result.put(key,value.toPlainString());
        }
        if(result.containsKey("outerDiameter"))greater(result,"outerDiameter","innerDiameter","外径必须大于内径");
        if("梯形".equals(type)){
            greater(result,"upperOuterDiameter","upperInnerDiameter","上外径必须大于上内径");
            if(decimal(result,"upperInnerDiameter").compareTo(decimal(result,"lowerInnerDiameter"))<0)throw new ServiceException("上内径不得小于下内径");
        }
        if("方形坩埚".equals(type))for(String key:List.of("Length","Width","Height"))greater(result,key.toLowerCase(),"inner"+key,"方形坩埚的外尺寸必须大于对应内尺寸");
        return result;
    }
    private static void greater(Map<String,String> d,String a,String b,String error){if(decimal(d,a).compareTo(decimal(d,b))<=0)throw new ServiceException(error);}
    private static BigDecimal decimal(Map<String,String> d,String key){return new BigDecimal(d.get(key));}
    public static BigDecimal volume(String type,Map<String,String> d,BigDecimal pi,BigDecimal factor){
        if(!Set.of("方形坩埚","方形盖").contains(type)&&(pi==null||pi.signum()<=0))return null;
        BigDecimal two=BigDecimal.valueOf(2),value;
        switch(type){
            case "环" -> value=decimal(d,"outerDiameter").divide(two).pow(2).subtract(decimal(d,"innerDiameter").divide(two).pow(2)).multiply(pi).multiply(decimal(d,"thickness"));
            case "杆" -> value=decimal(d,"innerDiameter").add(decimal(d,"outerDiameter")).divide(two).multiply(pi).multiply(decimal(d,"outerDiameter").subtract(decimal(d,"innerDiameter"))).multiply(decimal(d,"length"));
            case "梯形" -> {
                BigDecimal inner=decimal(d,"upperInnerDiameter"),wall=decimal(d,"wallThickness");
                value=inner.add(decimal(d,"upperOuterDiameter")).divide(two).multiply(pi).multiply(wall).multiply(decimal(d,"longInnerHeight"))
                    .add(pi.multiply(wall).multiply(inner.add(decimal(d,"lowerInnerDiameter"))).divide(two).multiply(inner.subtract(decimal(d,"lowerInnerDiameter"))).divide(cosDegrees(decimal(d,"angle")),MathContext.DECIMAL128))
                    .add(inner.add(wall.multiply(two)).divide(two).pow(2).multiply(pi).multiply(decimal(d,"bottomThickness")));
            }
            case "靶" -> value=decimal(d,"innerDiameter").add(decimal(d,"wallThickness").multiply(two)).divide(two).pow(2).multiply(pi).multiply(decimal(d,"bottomThickness"));
            case "方形坩埚" -> value=decimal(d,"length").multiply(decimal(d,"width")).multiply(decimal(d,"height")).subtract(decimal(d,"innerLength").multiply(decimal(d,"innerWidth")).multiply(decimal(d,"innerHeight")));
            case "方形盖" -> value=decimal(d,"length").multiply(decimal(d,"width")).multiply(decimal(d,"height"));
            case "平底坩埚","弧底" -> {
                BigDecimal inner=decimal(d,"innerDiameter"),wall=decimal(d,"wallThickness");
                value=inner.add(decimal(d,"outerDiameter")).divide(two).multiply(pi).multiply(wall).multiply(decimal(d,"innerHeight"))
                    .add(inner.add(wall.multiply(two)).divide(two).pow(2).multiply(pi).multiply(decimal(d,"bottomThickness")));
                if("弧底".equals(type)){
                    if(factor==null)return null;
                    if(factor.signum()<=0)throw new ServiceException("弧底系数须大于0");
                    value=value.multiply(factor);
                }
            }
            default -> throw new ServiceException("请选择有效产品类型");
        }
        return BusinessDecimal.positive(value,"计算体积");
    }
    static BigDecimal cosDegrees(BigDecimal angle){
        // Exact special angles avoid an approximation changing HALF_UP at a half-cent boundary.
        if(angle.signum()==0)return BigDecimal.ONE;
        if(angle.compareTo(BigDecimal.valueOf(60))==0)return new BigDecimal("0.5");
        MathContext mc=new MathContext(50,RoundingMode.HALF_UP);
        BigDecimal radians=angle.multiply(new BigDecimal("3.14159265358979323846264338327950288419716939937510"),mc).divide(BigDecimal.valueOf(180),mc);
        BigDecimal square=radians.multiply(radians,mc).negate(),term=BigDecimal.ONE,sum=BigDecimal.ONE;
        for(int n=1;n<100;n++){
            term=term.multiply(square,mc).divide(BigDecimal.valueOf((2L*n-1)*(2L*n)),mc);sum=sum.add(term,mc);
            if(term.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-48))<0)return sum;
        }
        throw new ServiceException("夹角计算失败，请核对输入");
    }
    public static BigDecimal weight(BigDecimal volume,Object density){return volume==null||density==null?null:BusinessDecimal.positive(BusinessDecimal.round(volume).multiply(new BigDecimal(density.toString())),"理论成品净重");}
    public static BigDecimal parameter(Map<String,Object> rule,String key){Object value=rule.get(key);return value==null?null:new BigDecimal(value.toString());}
    public static String specification(String type,Map<String,String> dimensions){
        return FIELDS.get(type).stream().map(k->label(type,k)+new BigDecimal(dimensions.get(k)).stripTrailingZeros().toPlainString()+("angle".equals(k)?"°":"cm")).collect(java.util.stream.Collectors.joining("；"));
    }
}
