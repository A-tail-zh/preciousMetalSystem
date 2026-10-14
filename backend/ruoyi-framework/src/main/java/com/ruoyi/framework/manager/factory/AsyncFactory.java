package com.ruoyi.framework.manager.factory;

import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.LogUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.UserAgentUtils;
import com.ruoyi.common.utils.ip.AddressUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.service.ISysLogininforService;
import com.ruoyi.system.service.ISysOperLogService;

/**
 * 异步工厂（产生任务用）
 * 
 * @author ruoyi
 */
public class AsyncFactory
{
    private static final Logger sys_user_logger = LoggerFactory.getLogger("sys-user");

    /**
     * 记录登录信息
     * 
     * @param username 用户名
     * @param status 状态
     * @param message 消息
     * @param args 列表
     * @return 任务task
     */
    public static TimerTask recordLogininfor(final String username, final String status, final String message,
            final Object... args)
    {
        final String auditTrace = org.slf4j.MDC.get("traceId");
        final String userAgent = ServletUtils.getRequest().getHeader("User-Agent");
        final String ip = IpUtils.getIpAddr();
        return new TimerTask()
        {
            @Override
            public void run()
            {
                org.slf4j.MDC.put("traceId",auditTrace==null?"":auditTrace);
                String address = AddressUtils.getRealAddressByIP(ip);
                StringBuilder s = new StringBuilder();
                s.append(LogUtils.getBlock(ip));
                s.append(address);
                s.append(LogUtils.getBlock(username));
                s.append(LogUtils.getBlock(status));
                s.append(LogUtils.getBlock(message));
                // 打印信息到日志
                sys_user_logger.info(s.toString(), args);
                // 获取客户端操作系统
                String os = UserAgentUtils.getOperatingSystem(userAgent);
                // 获取客户端浏览器
                String browser = UserAgentUtils.getBrowser(userAgent);
                // 封装对象
                SysLogininfor logininfor = new SysLogininfor();
                logininfor.setEventId(java.util.UUID.randomUUID().toString());
                logininfor.setReasonCategory(status.equals(Constants.LOGOUT)?"LOGOUT":message.contains("验证码")?"CAPTCHA_REJECTED":status.equals(Constants.LOGIN_FAIL)?"LOGIN_REJECTED":"LOGIN_SUCCESS");
                logininfor.setTraceId(auditTrace);
                logininfor.setUserName(com.ruoyi.framework.audit.AuditRedactor.text(username));
                logininfor.setIpaddr(ip);
                logininfor.setLoginLocation(address);
                logininfor.setBrowser(browser);
                logininfor.setOs(os);
                logininfor.setMsg(com.ruoyi.framework.audit.AuditRedactor.text(message));
                // 日志状态
                if (StringUtils.equalsAny(status, Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER))
                {
                    logininfor.setStatus(Constants.SUCCESS);
                }
                else if (Constants.LOGIN_FAIL.equals(status))
                {
                    logininfor.setStatus(Constants.FAIL);
                }
                // 插入数据
                try{SpringUtils.getBean(ISysLogininforService.class).insertLogininfor(logininfor);}catch(Exception unavailable){java.util.Map<String,Object> event=new java.util.LinkedHashMap<>();event.put("kind","login");event.put("event",logininfor.getEventId());event.put("trace",auditTrace);event.put("actor",logininfor.getUserName());event.put("ip",ip);event.put("browser",browser);event.put("status",logininfor.getStatus());event.put("reason",logininfor.getReasonCategory());event.put("message",logininfor.getMsg());event.put("time",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString());com.ruoyi.framework.audit.AuditRecorder.fallback(event);}finally{org.slf4j.MDC.remove("traceId");}
            }
        };
    }

    /**
     * 操作日志记录
     * 
     * @param operLog 操作日志信息
     * @return 任务task
     */
    public static TimerTask recordOper(final SysOperLog operLog)
    {
        return new TimerTask()
        {
            @Override
            public void run()
            {
                // 远程查询操作地点
                operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
                SpringUtils.getBean(ISysOperLogService.class).insertOperlog(operLog);
            }
        };
    }
}
