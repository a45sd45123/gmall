package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.annotation.LoginRequire;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.enums.PaymentWay;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Controller
public class OrderController {

    @Reference
    SkuService skuService;

    @Reference
    CartService cartService;

    @Reference
    UserService userService;

    @Reference
    OrderService orderService;

    //跳转到结算页面操作
    @LoginRequire(ifNeedSuccess = true)
    @RequestMapping("toTrade")
    private String toTrade(HttpServletRequest request, HttpServletResponse response, ModelMap map){
        String userId = (String)request.getAttribute("userId");
        // 将被选中的购物车对象转化为订单对象，展示出来
        List<CartInfo> cartInfos = cartService.getCartCacheByChecked(userId);
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (CartInfo cartInfo : cartInfos) {
            //将购物车对象转化为订单对象
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setOrderPrice(cartInfo.getCartPrice());
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetails.add(orderDetail);
        }
        // 查询用户收获地址列表，让用户选择
        List<UserAddress> userAddressList = userService.getUserAddressList(userId);

        // 生成交易码,并存放到缓存中
        String tradeCode = orderService.genTradeCode(userId);
        map.put("tradeCode", tradeCode);
        map.put("userAddressList", userAddressList);
        map.put("orderDetailList", orderDetails);
        map.put("totalAmount", getTotalPrice(cartInfos));
        return "trade";
    }


    @LoginRequire(ifNeedSuccess = true)
    @RequestMapping("submitOrder")
    private String submitOrder(String tradeCode,HttpServletRequest request,HttpServletResponse response,ModelMap map){
        //获取用户Id
        String userId = (String)request.getAttribute("userId");
        //比较交易码，true为交易码校验成功，删除缓存中的交易码
       Boolean b = orderService.checkTradeCode(tradeCode,userId);

       //订单对象
        OrderInfo orderInfo = new OrderInfo();
        List<OrderDetail> orderDetails = new ArrayList<>();
        //生成订单信息，校验价格，库存
       if(b){
            //获取购物车中选中的商品列表，以购物车为准
           List<CartInfo> cartInfos = cartService.getCartCacheByChecked(userId);
           for (CartInfo cartInfo : cartInfos) {
               OrderDetail orderDetail = new OrderDetail();
               //校验价格
               String skuId = cartInfo.getSkuId();
               BigDecimal skuPrice = cartInfo.getSkuPrice();
               Boolean bprice = skuService.checkPrice(skuId,skuPrice);
               if(bprice){
                   //验证库存信息
                   orderDetail.setSkuName(cartInfo.getSkuName());
                   orderDetail.setSkuId(cartInfo.getSkuId());
                   orderDetail.setOrderPrice(cartInfo.getCartPrice());
                   orderDetail.setImgUrl(cartInfo.getImgUrl());
                   orderDetail.setSkuNum(cartInfo.getSkuNum());
                   orderDetails.add(orderDetail);
               }else {
                   // sku校验失败
                   map.put("errMsg","订单中的商品价格(库存)发生了变化，请重新确认订单");
                   return "tradeFail";
               }

           }
           //封装订单
           orderInfo.setOrderDetailList(orderDetails);
           orderInfo.setProcessStatus("订单未支付");
           Calendar calendar = Calendar.getInstance();
           calendar.add(Calendar.DATE,1);
           orderInfo.setExpireTime(calendar.getTime());
           orderInfo.setOrderStatus("未支付");
           orderInfo.setConsignee("测试收件人");
           //外部订单号 为支付宝服务的订单号，方便支付宝识别
           SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddmmss");
           String format = sdf.format(new Date());
           String outTradeNo = "ATGUIGU"+format+System.currentTimeMillis();
           orderInfo.setOutTradeNo(outTradeNo);

           orderInfo.setUserId(userId);
           orderInfo.setTotalAmount(getTotalPrice(cartInfos));
           orderInfo.setPaymentWay(PaymentWay.ONLINE);
           orderInfo.setConsigneeTel("123123123123");
           orderInfo.setDeliveryAddress("测试收货地址");
           orderInfo.setOrderComment("硅谷订单");

           //保存orderInfo到数据库中，并更新orderDetail中的orderId
           String orderId = orderService.saveOrder(orderInfo);
           //删除数据库中的购物车列表信息，并将删除后的数据库同步到缓存redis中
           cartService.deleteCartById(cartInfos);
           // 对接支付系统接口
           return "redirect:http://payment.gmall.com:8087/index?orderId="+orderId;
       }else {
           map.put("errMsg","获取订单信息失败");
           return "tradeFail";
       }

    }

    //添加获取购物车选中商品的总金额
    private BigDecimal getTotalPrice(List<CartInfo> cartInfos) {
        BigDecimal b = new BigDecimal("0");
        for (CartInfo cartInfo : cartInfos) {

            if(cartInfo.getIsChecked().equals("1")){
                b = b.add(cartInfo.getCartPrice());
            }

        }
        return b;
    }
}

