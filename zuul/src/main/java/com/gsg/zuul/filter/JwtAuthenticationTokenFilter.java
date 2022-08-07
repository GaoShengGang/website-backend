package com.gsg.zuul.filter;

import com.gsg.commons.utils.Constants;
import com.gsg.commons.utils.DateFormateUtils;
import com.gsg.commons.utils.RedisUtils;
import com.gsg.commons.utils.WebPUtils;
import com.gsg.zuul.config.JwtProperties;
import com.gsg.zuul.model.JwtUserDetails;
import com.gsg.zuul.service.impl.UserDetailServiceImpl;
import com.gsg.zuul.utils.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.gsg.commons.utils.Constants.*;

/**
 * @Description: 过滤器
 * @Author shuaigang
 * @Date 2021/9/29 15:51
 */
@Component
@Slf4j
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static Float webPScale = 1.0f;

    @Resource
    private UserDetailServiceImpl userDetailsService;

    @Resource
    private JwtTokenUtil jwtTokenUtil;

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private RedisUtils redisUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        log.info("JwtAuthenticationTokenFilter过滤器2开始执行");
        String authToken = request.getHeader(jwtProperties.getHeader());
        String userId = jwtTokenUtil.getUsernameFromToken(authToken);
        boolean flag = false;
        if (!StringUtils.isEmpty(authToken) && !"".equals(authToken)) {
            // token刷新时间
            flag = true;
            if (!jwtTokenUtil.isTokenExpired(authToken)) {
                authToken = jwtTokenUtil.refreshToken(authToken);
                log.debug("Token时间已刷新!{}", jwtTokenUtil.getExpirationDateFromToken(authToken));
            }
            if (userId == null || jwtTokenUtil.isTokenExpired(authToken)) {
                response.setHeader(IS_EXPIRED, FLAG);
                log.debug("Token已失效");
                redisUtils.delete(userId);
                flag = false;
            }

            /*TODO token交由redis管理时效*/
//            flag = true;
//            Boolean tokenExistFlag = redisUtils.isExist(authToken);
//            if(tokenExistFlag){
//                redisUtils.setExpire(authToken, jwtProperties.getTokenValidityInSeconds(), TimeUnit.SECONDS);
//                log.info("[{}] Token时间已刷新!【{}--{}】", DateFormateUtils.getUtcCurrentDate(),authToken, redisUtils.getExpire(authToken));
//            }
//            if (userId == null || !tokenExistFlag) {
//                response.setHeader(IS_EXPIRED, FLAG);
//                log.info("Token已失效");
//                redisUtils.delete(userId);
//                flag = false;
//            }

        }

        log.debug("进入自定义过滤器：");
        log.debug("自定义过滤器获得用户名为: {}", userId);

        // 当token中的username不为空时进行验证token是否是有效的token
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null && flag) {


            /**
             * Token副本校验
             * 在redis中存储token副本，用户请求时候校验，如果redis中不存在该副本则不给通过。
             */
//            String accessSysCode = request.getHeader("accessSysCode");
//            if(StringUtils.isEmpty(accessSysCode)){
//                accessSysCode = (String)JSON.parseObject(request.getParameter(META_DATA)).get("accessSysCode");
//            }

            // token中username不为空，进行token验证
            /* 从数据库得到带有密码的完整user信息*/
            JwtUserDetails userDetails = this.userDetailsService.loadUserByUsername(userId);

            if (jwtTokenUtil.validateToken(authToken, userDetails)
                    && redisUtils.isExist(userDetails.getUsername())
            ) {

                log.info("验证通过，将验证信息放入上下文中");
                /**
                 * UsernamePasswordAuthenticationToken继承AbstractAuthenticationToken实现Authentication
                 * 所以当在页面中输入用户名和密码之后首先会进入到UsernamePasswordAuthenticationToken验证(Authentication)，
                 * 然后生成的Authentication会被交由AuthenticationManager来进行管理
                 * 而AuthenticationManager管理一系列的AuthenticationProvider，
                 * 而每一个Provider都会通UserDetailsService和UserDetail来返回一个
                 * 以UsernamePasswordAuthenticationToken实现的带用户名和密码以及权限的Authentication
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 将authentication放入SecurityContextHolder中
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        log.info("请求URL:【{}】Method:【{}】", requestURI, method);


        /*requestURI = /gsg/static-resource/formal/2/20211014/123456.png*/
        if (method.equals("GET")
                // 本地
//                 && requestURI.startsWith("/D:/static-resource/formal")
                // 开发
                && requestURI.startsWith("/gsg/static-resource/formal")
                && (requestURI.endsWith(".png")
                || requestURI.endsWith(".jpg")
                || requestURI.endsWith(".jpeg")
                || requestURI.endsWith(".gif")
                || requestURI.endsWith(".bmp")
                || requestURI.endsWith(".webp"))) {
            /** 20211210 如果请求GET 方式获取图片资源文件时，将图片转换成webp格式，进行返回*/
            int beginIndex = requestURI.lastIndexOf("/");
            /*/gsg/static-resource/formal/2/20211014*/
            String imgFilePath = requestURI.substring(0, beginIndex);

            /** 20211210 生成WebP图片副本*/
            beginIndex = requestURI.lastIndexOf(".");
            String imgFilePrefix = requestURI.substring(0, beginIndex);
            String imgWebPFile = imgFilePrefix + ".webp";
            log.info("请求图片路径:【{}】对应WebP图片:【{}】", imgFilePath, imgWebPFile);

            /** 判断副本图片不存在就生成，*/
            File webPFile = new File(imgWebPFile);
            if (!webPFile.exists()) {
                log.info("调用方法生成WebP副本");
                if (requestURI.contains(".webp")) {
                    String imgPngFilePath = imgFilePrefix + ".png";
                    String imgGifFilePath = imgFilePrefix + ".gif";
                    String imgJpgFilePath = imgFilePrefix + ".jpg";
                    String imgBmpFilePath = imgFilePrefix + ".bmp";
                    String imgJpegFilePath = imgFilePrefix + ".jpeg";
                    File imgPngFile = new File(imgPngFilePath);
                    if (imgPngFile.exists()) {
                        WebPUtils.encodingToWebp(imgPngFilePath, imgWebPFile, webPScale);
                    }

                    File imgGifFile = new File(imgGifFilePath);
                    if (imgGifFile.exists()) {
                        WebPUtils.encodingToWebp(imgGifFilePath, imgWebPFile, webPScale);
                    }

                    File imgJpgFile = new File(imgJpgFilePath);
                    if (imgJpgFile.exists()) {
                        WebPUtils.encodingToWebp(imgJpgFilePath, imgWebPFile, webPScale);
                    }

                    File imgBmpFile = new File(imgBmpFilePath);
                    if (imgBmpFile.exists()) {
                        WebPUtils.encodingToWebp(imgBmpFilePath, imgWebPFile, webPScale);
                    }

                    File imgJpegFile = new File(imgJpegFilePath);
                    if (imgJpegFile.exists()) {
                        WebPUtils.encodingToWebp(imgJpegFilePath, imgWebPFile, webPScale);
                    }
                } else {
                    WebPUtils.encodingToWebp(requestURI, imgWebPFile, webPScale);
                }
            }

            if (!requestURI.contains(".webp")) {
                /** 20211124 前端增加header参数，判断是否支持WebP格式图片访问*/
                String supportWebp = request.getHeader("supportWebp");
//                String serverName = request.getHeader("serverName");
                if (!StringUtils.isEmpty(supportWebp)
                        && supportWebp.trim().equals("1")) {
//
//                    if (ObjectUtils.isEmpty(serverName)) {
//                        serverName = request.getServerName() + ":" + request.getServerPort();
//                    }
//                    if(!ObjectUtils.isEmpty(request.getScheme())){
//                        serverName = request.getScheme() + "://" + serverName;
//                    }
                    log.info("请求转发图片副本：【{}】", imgWebPFile);
                    request.getRequestDispatcher(imgWebPFile).forward(request, response);
                }

            }

        }

        log.info("过滤器2执行结束!");
        chain.doFilter(request, response);

    }
}
