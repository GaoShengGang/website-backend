package com.gsg.zuul.handler;

import com.alibaba.fastjson.JSON;
import com.gsg.commons.utils.JacksonUtils;
import com.gsg.commons.utils.R;
import com.gsg.commons.utils.Result;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.gsg.commons.utils.Constants.FLAG;
import static com.gsg.commons.utils.Constants.IS_EXPIRED;

/**
 * @Description: 认证失败处理类
 * 当用户没有携带有效凭证时，就会转到这里来，当然，我们还需要在Spring Security的配置类中指定我们自定义的处理类才可以
 * @Author shuaigang
 * @Date 2021/9/29 11:58
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException, ServletException {
        // 响应JSON格式
        response.setContentType("application/json;charset=utf-8");
        String json;

        if (FLAG.equals(response.getHeader(IS_EXPIRED))) {
            log.debug("token已失效!");
            json = JacksonUtils.obj2json(R.construct(R.TOKEN_IS_EXPIRED, "登录已失效！!"));
        } else {
            log.debug("未认证!");
            json = JacksonUtils.obj2json(R.construct(R.UNAUTHORIZED, "未认证登录!"));
        }
        response.getWriter().write(json);
    }
}
