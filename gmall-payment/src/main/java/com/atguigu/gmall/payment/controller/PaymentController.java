package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall.annotation.LoginRequire;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Autowired
    PaymentService paymentService;

    @Autowired
    AlipayClient alipayClient;

    @Reference
    OrderService orderService;

    @LoginRequire(ifNeedSuccess =true)
    @RequestMapping("index")
    private String index(HttpServletRequest request,String orderId, ModelMap map){

        //通过orderId查询数据库中的order表，但是没有orderDetailList,
        // 需要通过orderId,查询出detail对象列表封装进去
        OrderInfo orderInfo = orderService.selectByOrderId(orderId);

        map.put("userId",request.getAttribute("userId"));
        map.put("nickName" ,request.getAttribute("nickName"));
        map.put("orderInfo",orderInfo);
        map.put("orderId",orderId);
        map.put("outTradeNo",orderInfo.getOutTradeNo());
        map.put("totalAmount",orderInfo.getTotalAmount());
        return "index";
    }

    @LoginRequire(ifNeedSuccess =true)
    @RequestMapping("alipay/submit")
    @ResponseBody
    private  String alipay(HttpServletRequest request,String orderId,ModelMap map){
        String userId = (String) request.getAttribute("userId");
        OrderInfo order = orderService.selectByOrderId(orderId);

        // 生成和保存支付信息
        PaymentInfo paymentInfo = new PaymentInfo();
        //外部对接支付宝的订单号
        paymentInfo.setOutTradeNo(order.getOutTradeNo());
        paymentInfo.setPaymentStatus("未支付");
        paymentInfo.setOrderId(orderId);
        paymentInfo.setTotalAmount(order.getTotalAmount());
        paymentInfo.setSubject(order.getOrderDetailList().get(0).getSkuName());
        paymentInfo.setCreateTime(new Date());

        paymentService.savePayment(paymentInfo);
        // 重定向到支付宝平台
        AlipayTradePagePayRequest payRequest = new AlipayTradePagePayRequest();
        payRequest.setReturnUrl(AlipayConfig.return_payment_url);
        //在公共参数中设置回跳和通知地址
        payRequest.setNotifyUrl(AlipayConfig.notify_payment_url);

        HashMap<String, Object> stringObjectHashMap = new HashMap<>();
        stringObjectHashMap.put("out_trade_no",order.getOutTradeNo());
        stringObjectHashMap.put("product_code","FAST_INSTANT_TRADE_PAY");
        stringObjectHashMap.put("total_amount",0.01);//orderById.getTotalAmount()
        stringObjectHashMap.put("subject","测试硅谷手机phone");
        //将设置信息转换为json字符串
        String json = JSON.toJSONString(stringObjectHashMap);
        //配置文件传入payrequest中
        payRequest.setBizContent(json);
        String form = "";
        try {
            form = alipayClient.pageExecute(payRequest).getBody(); //调用SDK生成表单
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        System.out.println("设置一个定时巡检订单"+paymentInfo.getOutTradeNo()+"的支付状态的延迟队列");
        paymentService.sendPaymentCheckQueue(paymentInfo.getOutTradeNo(),5);

        return form;

    }

    //支付成功后的回调函数
    @RequestMapping(value = "alipay/callback/return")
    public String callbackReturn(HttpServletRequest request, String orderId, ModelMap map){

        Map<String, String> paramsMap = null; //将异步通知中收到的所有参数都存放到map中
        boolean signVerified = true;
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type); //调用SDK验证签名
        } catch (Exception e) {
            System.out.println("此处支付宝的签名验证通过。。。");
        }


        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            String tradeNo = request.getParameter("trade_no");
            String outTradeNo = request.getParameter("out_trade_no");
            String tradeStatus = request.getParameter("trade_status");

            String callbackContent =request.getQueryString();
            // 幂等性检擦 延迟队列支付成功和此次支付成功重叠，故需要先判断是否已支付成功
            boolean b = paymentService.checkPaied(outTradeNo);
            if(!b){
                //支付成功后，发送支付成功消息队列到mq中，PAYMENT_SUCCESS_QUEUE，同时修改支付信息
                paymentService.sendPaymentSuccessQueue(tradeNo,outTradeNo,callbackContent);
            }


        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            // 返回失败页面
        }

        return "testPaySuccess";
    }

    @LoginRequire(ifNeedSuccess =true)
    @RequestMapping("mx/submit")
    @ResponseBody
    private  String mx(){
        return null;
    }
    // alipay/submit
    // mx/submit
}
