package com.tomorrowcat.couponapp.config;

import org.apache.dubbo.config.MetadataReportConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description: 配置dubbo的元数据信息，这样才dubbo管控台才能显示
 * @param: null
 * @return null
 * @author: kim
 * @date: 2021/7/27 10:09
 */
@Configuration
public class DubboConfig {

    @Bean
    public MetadataReportConfig metadataReportConfig() {
        MetadataReportConfig metadataReportConfig = new MetadataReportConfig();
        metadataReportConfig.setAddress("zookeeper://kim2:2181");
        return metadataReportConfig;
    }

}
