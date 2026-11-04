package com.ruoyi.framework.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.DemoModeException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.html.EscapeUtil;

/**
 * 全局异常处理器
 * 
 * @author ruoyi
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限校验异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public AjaxResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        return InputValidationErrors.response(java.util.List.of(InputValidationErrors.field(e.getName(),"TypeMismatch",InputValidationErrors.label(e.getName())+"格式不正确")));
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return AjaxResult.error("系统处理失败，请稍后重试或联系管理员");
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error("系统处理失败，请稍后重试或联系管理员");
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.warn("表单参数校验失败");
        return InputValidationErrors.from(e.getBindingResult());
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.warn("请求参数校验失败");
        return InputValidationErrors.from(e.getBindingResult());
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public AjaxResult handleConstraint(jakarta.validation.ConstraintViolationException e) {
        var errors=new java.util.ArrayList<java.util.Map<String,String>>();
        for(var violation:e.getConstraintViolations()){String field=violation.getPropertyPath().toString();String message=violation.getMessage();if(message==null||!message.matches(".*[\\u4e00-\\u9fff].*"))message=InputValidationErrors.label(field)+"格式或取值不合法";errors.add(InputValidationErrors.field(field,violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),message));}
        return InputValidationErrors.response(errors);
    }
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public AjaxResult handleMissingParameter(org.springframework.web.bind.MissingServletRequestParameterException e) {
        return InputValidationErrors.response(java.util.List.of(InputValidationErrors.field(e.getParameterName(),"Required",InputValidationErrors.label(e.getParameterName())+"不能为空")));
    }

    @ExceptionHandler(InputPayloadAdvice.InvalidFields.class)
    public AjaxResult handleInputFields(InputPayloadAdvice.InvalidFields e) { return InputValidationErrors.response(e.fields); }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public AjaxResult handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        Throwable cause=e; String path="";
        while(cause!=null){
            if(cause instanceof com.fasterxml.jackson.databind.JsonMappingException mapping){
                StringBuilder field=new StringBuilder();
                for(var reference:mapping.getPath()){if(reference.getFieldName()!=null){if(field.length()>0)field.append('.');field.append(reference.getFieldName());}else if(reference.getIndex()>=0)field.append('[').append(reference.getIndex()).append(']');}
                path=field.toString();break;
            }
            if(cause instanceof tools.jackson.core.JacksonException mapping){
                StringBuilder field=new StringBuilder();for(var reference:mapping.getPath()){if(reference.getPropertyName()!=null){if(field.length()>0)field.append('.');field.append(reference.getPropertyName());}else if(reference.getIndex()>=0)field.append('[').append(reference.getIndex()).append(']');}path=field.toString();break;
            }
            cause=cause.getCause();
        }
        return InputValidationErrors.response(java.util.List.of(InputValidationErrors.field(path,"TypeMismatch",InputValidationErrors.label(path)+"格式不正确，请检查类型、日期和取值")));
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
