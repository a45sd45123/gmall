package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.bean.PaymentInfo;

import java.util.Map;

public interface PaymentService {

    void savePayment(PaymentInfo paymentInfo);

    void updatePayment(PaymentInfo paymentInfo);

    Map<String,String> checkPayment(String outTradeNo);

    boolean checkPaied(String outTradeNo);

    void sendPaymentSuccessQueue(String tradeNo, String outTradeNo, String callbackContent);

    void sendPaymentCheckQueue(String outTradeNo, int i);
}
