package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    CartMapper cartMapper;

    @Autowired
    RedisUtil redisUtil;

    @Override
    public CartInfo ifCartExist(CartInfo cartInfo) {
        CartInfo cartInfo1 = new CartInfo();
        cartInfo1.setUserId(cartInfo.getUserId());
        cartInfo1.setSkuId(cartInfo.getSkuId());
        return cartMapper.selectOne(cartInfo1);
    }

    @Override
    public void saveCart(CartInfo cartInfo) {
        cartMapper.insertSelective(cartInfo);
    }

    @Override
    public void updateCart(CartInfo cartInfoDb) {
        cartMapper.updateByPrimaryKeySelective(cartInfoDb);
    }

    //将数据同步到缓存中
    @Override
    public void syncCache(String userId) {

        Jedis jedis = redisUtil.getJedis();
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userId);
        List<CartInfo> select = cartMapper.select(cartInfo);
        HashMap<String, String> stringStringHashMap = new HashMap<>();
        for (CartInfo info : select) {
            stringStringHashMap.put(info.getId(),JSON.toJSONString(info));
        }
        jedis.hmset("carts:"+userId+":info",stringStringHashMap);

    }

    //查询出redis缓存中的所有CartInfo对象，即CartInfoList
    @Override
    public List<CartInfo> getCartCache(String userId) {

        List<CartInfo> cartInfos = new ArrayList<>();
        Jedis jedis = redisUtil.getJedis();

        List<String> hvals = jedis.hvals("carts:" + userId + ":info");

        if(hvals != null&&hvals.size()>0){
            for (String hval : hvals) {
                CartInfo cartInfo = JSON.parseObject(hval, CartInfo.class);
                cartInfos.add(cartInfo);
            }
        }
        return cartInfos;
    }

    @Override
    public void updateCartChecked(CartInfo cartInfo) {
        //例如查询语句 update table set a=1,b=2 where id1 =? and id2 = ?
        Example e = new Example(CartInfo.class);
        //添加指定的过滤条件 skuId 和 userId
        e.createCriteria().andEqualTo("skuId",cartInfo.getSkuId()).andEqualTo("userId",cartInfo.getUserId());
        //selective: carInfo 为对象中哪几个字段有值就更新哪几个字段， ByExample: e 为提供where后面的查询条件
        cartMapper.updateByExampleSelective(cartInfo,e);
        syncCache(cartInfo.getUserId());
    }


    //登录成功后合并购物车商品
    @Override
    public void combineCart(List<CartInfo> cartInfos, String userId) {
        if(cartInfos!=null){
            for (CartInfo cartInfo : cartInfos) {
                //查看数据库中是否有该商品
                CartInfo info = ifCartExist(cartInfo);
                if(info==null){
                    // 没有该商品，插入
                    cartInfo.setUserId(userId);
                    cartMapper.insertSelective(cartInfo);
                }else{
                    // 有该商品，合并更新
                    info.setSkuNum(cartInfo.getSkuNum()+info.getSkuNum());
                    info.setCartPrice(info.getSkuPrice().multiply(new BigDecimal(info.getSkuNum())));
                    cartMapper.updateByPrimaryKeySelective(info);
                }
            }
        }
        // 同步缓存
        syncCache(userId);
    }


    //获取缓存中被选中的商品集合，用于生成订单Order
    @Override
    public List<CartInfo> getCartCacheByChecked(String userId) {
        List<CartInfo> cartInfos = new ArrayList<>();
        Jedis jedis = redisUtil.getJedis();
        //查询出当前登录用户缓存中所有的购物车商品
        List<String> hvals = jedis.hvals("carts:" + userId + ":info");
        if(hvals!=null&&hvals.size()>0){
            for (String hval : hvals) {
                CartInfo cartInfo = JSON.parseObject(hval, CartInfo.class);
                if(cartInfo.getIsChecked().equals("1")){
                    cartInfos.add(cartInfo);
                }
            }
        }
        jedis.close();
        return cartInfos;
    }

    //提交订单时，删除数据库中的购物车列表，将删除后的数据库同步到缓存中
    @Override
    public void deleteCartById(List<CartInfo> cartInfos) {

        //删除数据库中的购物车列表
        for (CartInfo cartInfo : cartInfos) {

            cartMapper.deleteByPrimaryKey(cartInfo);
        }
        //同步缓存
        syncCache(cartInfos.get(0).getUserId());
    }
}
