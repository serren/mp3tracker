package com.example.resourceservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.processed-queue}")
    private String processedQueue;

    @Value("${rabbitmq.processed-routing-key}")
    private String processedRoutingKey;

    @Bean
    public DirectExchange resourcesExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue resourcesQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding resourcesBinding(Queue resourcesQueue, DirectExchange resourcesExchange) {
        return BindingBuilder.bind(resourcesQueue).to(resourcesExchange).with(routingKey);
    }

    @Bean
    public Queue resourceProcessedQueue() {
        return new Queue(processedQueue, true);
    }

    @Bean
    public Binding resourceProcessedBinding(Queue resourceProcessedQueue, DirectExchange resourcesExchange) {
        return BindingBuilder.bind(resourceProcessedQueue).to(resourcesExchange).with(processedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
