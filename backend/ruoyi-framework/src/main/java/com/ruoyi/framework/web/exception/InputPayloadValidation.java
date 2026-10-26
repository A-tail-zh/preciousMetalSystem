package com.ruoyi.framework.web.exception;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;

/** Check numeric tokens before binding: Jackson must not truncate fractional integers. */
public final class InputPayloadValidation {
 private InputPayloadValidation() {}
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final class NumericToken extends com.fasterxml.jackson.databind.node.TextNode {NumericToken(String text){super(text);}}
 /** Preserve original numeric tokens for validation only; original request bytes are never rewritten. */
 public static JsonNode parse(String text)throws java.io.IOException{return parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public static JsonNode parse(byte[] bytes)throws java.io.IOException{
  try(var parser=JSON.getFactory().createParser(bytes)){parser.nextToken();JsonNode node=read(parser);if(parser.nextToken()!=null)throw new com.fasterxml.jackson.core.JsonParseException(parser,"Trailing content");return node;}
 }
 private static JsonNode read(com.fasterxml.jackson.core.JsonParser parser)throws java.io.IOException {
  var token=parser.currentToken();if(token==null)throw new com.fasterxml.jackson.core.JsonParseException(parser,"Empty input");
  if(token==com.fasterxml.jackson.core.JsonToken.START_OBJECT){var object=JSON.createObjectNode();while(parser.nextToken()!=com.fasterxml.jackson.core.JsonToken.END_OBJECT){String name=parser.currentName();parser.nextToken();object.set(name,read(parser));}return object;}
  if(token==com.fasterxml.jackson.core.JsonToken.START_ARRAY){var array=JSON.createArrayNode();while(parser.nextToken()!=com.fasterxml.jackson.core.JsonToken.END_ARRAY)array.add(read(parser));return array;}
  if(token.isNumeric())return new NumericToken(parser.getText());
  if(token==com.fasterxml.jackson.core.JsonToken.VALUE_STRING)return com.fasterxml.jackson.databind.node.TextNode.valueOf(parser.getText());
  if(token==com.fasterxml.jackson.core.JsonToken.VALUE_TRUE||token==com.fasterxml.jackson.core.JsonToken.VALUE_FALSE)return com.fasterxml.jackson.databind.node.BooleanNode.valueOf(token==com.fasterxml.jackson.core.JsonToken.VALUE_TRUE);
  if(token==com.fasterxml.jackson.core.JsonToken.VALUE_NULL)return com.fasterxml.jackson.databind.node.NullNode.instance;
  throw new com.fasterxml.jackson.core.JsonParseException(parser,"Invalid JSON token");
 }
 public static List<Map<String,String>> check(JsonNode node,Type type){var out=new ArrayList<Map<String,String>>();visit(node,type,"",out,0);return out;}
 private static void visit(JsonNode node,Type type,String path,List<Map<String,String>> out,int depth){
  if(node==null||depth>30||out.size()>=100)return;
  if(node.isNull()){if(type instanceof Class<?> c&&c.isPrimitive())out.add(InputValidationErrors.field(path,"Required",InputValidationErrors.label(path)+"不能为空"));return;}
  if(type instanceof ParameterizedType p){if(Map.class.isAssignableFrom((Class<?>)p.getRawType())&&path.endsWith("dimensions")&&node.isObject()){var iterator=node.fields();while(iterator.hasNext()){var entry=iterator.next();if((entry.getValue() instanceof NumericToken||entry.getValue().isNumber())&&!entry.getValue().asText().matches("[0-9]+(\\.[0-9]{1,6})?"))out.add(InputValidationErrors.field(path+"."+entry.getKey(),"DecimalFormat","尺寸须为普通十进制数字，最多6位小数，不支持科学计数法"));}return;}if(Collection.class.isAssignableFrom((Class<?>)p.getRawType())&&node.isArray()){int i=0;for(var child:node)visit(child,p.getActualTypeArguments()[0],path+"["+(i++)+"]",out,depth+1);}return;}
  if(!(type instanceof Class<?> c))return;
  if(c==Boolean.class||c==boolean.class){if(!node.isBoolean())out.add(InputValidationErrors.field(path,"BooleanFormat",InputValidationErrors.label(path)+"须为有效的勾选或启用状态"));return;}
  if(c==java.time.LocalDate.class||c==java.time.LocalDateTime.class){
   try{if(!node.isTextual())throw new IllegalArgumentException();if(c==java.time.LocalDate.class){if(!node.asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();java.time.LocalDate.parse(node.asText());}else{java.time.LocalDateTime.parse(node.asText(),java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT));}}
   catch(RuntimeException e){out.add(InputValidationErrors.field(path,"DateFormat",InputValidationErrors.label(path)+"须为有效日期或时间"));}return;
  }
  if(c.isArray()&&node.isArray()){int i=0;for(var child:node)visit(child,c.getComponentType(),path+"["+(i++)+"]",out,depth+1);return;}
  if(c==Integer.class||c==int.class||c==Long.class||c==long.class||c==Short.class||c==short.class||c==Byte.class||c==byte.class||c==BigInteger.class||c==BigDecimal.class){
   boolean decimal=c==BigDecimal.class;String text=node.asText();boolean valid=(node.isTextual()||node.isNumber())&&text.matches(decimal?"[+-]?[0-9]+(\\.[0-9]+)?":"-?[0-9]+");
   if(valid&&!decimal)try{var n=new BigInteger(text);if(c!=BigInteger.class){long min=c==int.class||c==Integer.class?Integer.MIN_VALUE:c==short.class||c==Short.class?Short.MIN_VALUE:c==byte.class||c==Byte.class?Byte.MIN_VALUE:Long.MIN_VALUE;long max=c==int.class||c==Integer.class?Integer.MAX_VALUE:c==short.class||c==Short.class?Short.MAX_VALUE:c==byte.class||c==Byte.class?Byte.MAX_VALUE:Long.MAX_VALUE;valid=n.compareTo(BigInteger.valueOf(min))>=0&&n.compareTo(BigInteger.valueOf(max))<=0;}}catch(NumberFormatException e){valid=false;}
   if(!valid)out.add(InputValidationErrors.field(path,decimal?"DecimalFormat":"IntegerFormat",InputValidationErrors.label(path)+(decimal?"须填写普通十进制数字，不支持科学计数法":"须填写范围内的整数，不支持小数或科学计数法")));return;
  }
  if(!c.getName().startsWith("com.ruoyi.")||!node.isObject())return;
  for(Class<?> current=c;current!=null&&current!=Object.class;current=current.getSuperclass())for(var field:current.getDeclaredFields()){if(Modifier.isStatic(field.getModifiers()))continue;String name=field.getName();visit(node.get(name),field.getGenericType(),path.isEmpty()?name:path+"."+name,out,depth+1);}
 }
}
