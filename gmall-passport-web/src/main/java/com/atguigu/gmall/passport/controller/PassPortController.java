package com.atguigu.gmall.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.util.CookieUtil;
import com.atguigu.gmall.util.JwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PassPortController {

    @Reference
    UserService userService;

    @Reference
    CartService cartService;

    @RequestMapping("index")
    private  String  index(String returnURL, ModelMap map){
        map.put("returnURL",returnURL);
        return "index";
    }


    //颁发token
    @RequestMapping("login")
    @ResponseBody
    public String login(HttpServletRequest request, HttpServletResponse response, UserInfo userInfo) {
        //查询用户是否登录
        UserInfo user = userService.login(userInfo);
        if (user == null) {
           return "username or password err";
        } else {
            //登录成功，登录信息已更新到reids缓存中
            //颁发token，重定向到原来的页面
            HashMap<String, String> stringStringHashMap = new HashMap<>();
            stringStringHashMap.put("userId", user.getId());
            stringStringHashMap.put("nickName", user.getNickName());
            String token = JwtUtil.encode("atguigu0328", stringStringHashMap, getMyIp(request));
            //合并cookie与数据库中的购物车，并同步到缓存中
            //1、获取cookie中的购物车对象集合
            List<CartInfo> cartInfos = null;
            String cartListCookie = CookieUtil.getCookieValue(request, "cartListCookie", true);
            //判断是否为空
            if(StringUtils.isNotBlank(cartListCookie)){
               cartInfos = JSON.parseArray(cartListCookie,CartInfo.class);
            }
            //合并购物车业务
            cartService.combineCart(cartInfos,user.getId());
            //清空cookie中的购物车列表
            CookieUtil.deleteCookie(request, response,"cartListCookie");
            return token;
        }
    }


    //验证token
    @RequestMapping("verify")
    @ResponseBody
    private  String  verify(String token,String salt){
        Map<String,String> userMap = null;
        try {
            userMap = JwtUtil.decode("atguigu0328",token,salt);
        }catch (Exception e){
            return "fail";
        }
        if(userMap!=null){
            return "success";
        }else{
            return "fail";
        }
    }

    //获取当前请求的IP地址：1.通过负载均衡发送的请求  2.直接发送的请求
    private String getMyIp(HttpServletRequest request){

        String ip = "";
        //通过负载均衡发送过来的请求，获取ip方法
        ip = request.getHeader("x-forwarded-for");
        if(StringUtils.isBlank(ip)){
            ip = request.getRemoteAddr();//直接获取ip
        }
        if(StringUtils.isBlank(ip)){
            ip = "127.0.0.1";//为了测试方便，设置一个虚拟ip，实际上返回错误提示。
        }
        return ip;

    }

}
