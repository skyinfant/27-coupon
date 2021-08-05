package com.tomorrowcat.couponapp.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
/**
*@description: 优雅关闭   7.5
*@author: kim
*@create: 2021/7/27 11:28
*@version: 1.0.0
*/
@Component
public class TestDisposeableBean implements DisposableBean {
    
    @Override
    public void destroy() throws Exception {
        System.out.println("测试TestDisposeableBean已经销毁");
    }
}
