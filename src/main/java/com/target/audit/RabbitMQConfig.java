package com.target.audit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.target.app.service.InstallationNameService;
import com.target.audittrail.util.AuditTrailConstants;

@Configuration
public class RabbitMQConfig {

  @Autowired private InstallationNameService installationNameService;

  @Bean
  Queue queue() {
    return QueueBuilder.durable(
            installationNameService.prefixInstallationName(AuditTrailConstants.QUEUE))
        .build();
  }

  @Bean
  TopicExchange exchange() {
    return new TopicExchange(AuditTrailConstants.TOPIC_EXCHANGE);
  }

  @Bean
  Binding binding(Queue queue, TopicExchange exchange) {
    return BindingBuilder.bind(queue)
        .to(exchange)
        .with(installationNameService.prefixInstallationName(AuditTrailConstants.QUEUE));
  }

  @Bean
  Queue edgeQueue() {
    return QueueBuilder.durable(
            installationNameService.prefixInstallationName(AuditTrailConstants.EDGE_QUEUE))
        .build();
  }

  @Bean
  Queue troveQueue() {
    return QueueBuilder.durable(
            installationNameService.prefixInstallationName(AuditTrailConstants.TROVE_QUEUE))
        .build();
  }

  @Bean
  Binding edgeBinding(Queue edgeQueue, TopicExchange exchange) {
    return BindingBuilder.bind(edgeQueue)
        .to(exchange)
        .with(installationNameService.prefixInstallationName(AuditTrailConstants.EDGE_QUEUE));
  }

  @Bean
  Binding troveBinding(Queue troveQueue, TopicExchange exchange) {
    return BindingBuilder.bind(troveQueue)
        .to(exchange)
        .with(installationNameService.prefixInstallationName(AuditTrailConstants.TROVE_QUEUE));
  }

  @Bean
  MessageListenerAdapter listenerAdapter(Receiver receiver) {
    MessageListenerAdapter adapter = new MessageListenerAdapter(receiver, "receiveMessage");
    SimpleMessageConverter converter = new SimpleMessageConverter();
    converter.addAllowedListPatterns("com.target.*", "java.util.*", "java.lang.*");
    adapter.setMessageConverter(converter);
    return adapter;
  }

  @Bean
  SimpleMessageListenerContainer container(
      ConnectionFactory connectionFactory, MessageListenerAdapter listenerAdapter) {
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setQueueNames(
        installationNameService.prefixInstallationName(AuditTrailConstants.QUEUE),
        installationNameService.prefixInstallationName(AuditTrailConstants.EDGE_QUEUE),
        installationNameService.prefixInstallationName(AuditTrailConstants.TROVE_QUEUE));
    container.setMessageListener(listenerAdapter);
    return container;
  }
}
