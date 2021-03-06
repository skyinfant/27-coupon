package com.tomorrowcat.couponapp.service.consumer;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * @description:
 * @author: kim
 * @create: 2021-08-02 21:57
 * @version: 1.0.0
 */
@Service
public class TestBaseConsumer extends BaseConsumer implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(TestBaseConsumer.class);

    @Value("${rocketmq.consumer.test.groupName}")
    private String groupName;
    @Value("${rocketmq.consumer.test.namesrvAddr}")
    private String namesrvAddr;
    @Value("${rocketmq.consumer.test.topic}")
    private String topic;
    @Value("${rocketmq.consumer.test.tag}")
    private String tag;



    /**
     * @description:  消费消息的逻辑
     * @param: msgs
     * @return: ConsumeConcurrentlyStatus
     */
    @Override
    public ConsumeConcurrentlyStatus dealBody(List<MessageExt> msgs) {
        msgs.forEach(msg -> {
            byte[] body = msg.getBody();
            try{
                String msgStr = new String(body, "utf-8");
                log.info(msgStr);
            } catch (UnsupportedEncodingException e) {
                log.error("body转字符串失败",e);

            }

        });
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * @description:  容器启动会执行此方法
     * @param: event
     * @return: void
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try{
            super.consume(groupName,namesrvAddr,topic, tag);
        } catch (MQClientException e) {
            log.error("消费者监听器启动失败", e);
        }

    }
}
















