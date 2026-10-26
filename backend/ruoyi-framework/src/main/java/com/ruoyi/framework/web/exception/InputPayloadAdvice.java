package com.ruoyi.framework.web.exception;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Read-only lexical validation: original request bytes reach the existing converter unchanged. */
@ControllerAdvice(basePackages="com.ruoyi.web.controller")
public class InputPayloadAdvice extends RequestBodyAdviceAdapter {
 public static class InvalidFields extends RuntimeException {final List<Map<String,String>> fields;InvalidFields(List<Map<String,String>> fields){super("输入内容不合法");this.fields=fields;}}
 @Override public boolean supports(MethodParameter parameter,Type target,Class<? extends HttpMessageConverter<?>> converter){return converter.getName().contains("Jackson")||converter.getName().contains("FastJson");}
 @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type target,Class<? extends HttpMessageConverter<?>> converter)throws IOException {
  byte[] bytes=input.getBody().readAllBytes();
  if(bytes.length>0){try{var fields=InputPayloadValidation.check(InputPayloadValidation.parse(bytes),target);if(!fields.isEmpty())throw new InvalidFields(fields);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new InvalidFields(List.of(InputValidationErrors.field("","JsonFormat","请求内容格式不正确，请检查输入")));}}
  return new HttpInputMessage(){public HttpHeaders getHeaders(){return input.getHeaders();}public InputStream getBody(){return new ByteArrayInputStream(bytes);}};
 }
}
