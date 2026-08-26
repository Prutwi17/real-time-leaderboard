package com.realtimeleaderboard.leaderboard.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void webSocketConfigBeanExists() {
        assertThat(context.getBean(WebSocketConfig.class)).isNotNull();
    }

    @Test
    void webSocketConfigImplementsConfigurer() {
        WebSocketConfig config = context.getBean(WebSocketConfig.class);
        assertThat(config).isInstanceOf(WebSocketMessageBrokerConfigurer.class);
    }

    @Test
    void webSocketConfigHasEnableAnnotation() {
        assertThat(WebSocketConfig.class.isAnnotationPresent(EnableWebSocketMessageBroker.class))
                .isTrue();
    }

    @Test
    void simpMessagingTemplateAvailable() {
        assertThat(context.getBean(org.springframework.messaging.simp.SimpMessagingTemplate.class))
                .isNotNull();
    }

    @Test
    void leaderboardUpdatePublisherAvailable() {
        assertThat(context.getBean(LeaderboardUpdatePublisher.class)).isNotNull();
    }
}
