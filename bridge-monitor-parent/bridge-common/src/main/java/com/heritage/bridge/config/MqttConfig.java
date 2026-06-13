package com.heritage.bridge.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.broker.connection-timeout}")
    private int connectionTimeout;

    @Value("${mqtt.broker.keep-alive-interval}")
    private int keepAliveInterval;

    @Value("${mqtt.broker.auto-reconnect}")
    private boolean autoReconnect;

    @Value("${mqtt.broker.clean-session}")
    private boolean cleanSession;

    @Value("${mqtt.broker.qos}")
    private int qos;

    @Value("${mqtt.broker.max-inflight}")
    private int maxInflight;

    @Value("${mqtt.broker.message-retry-interval}")
    private int messageRetryInterval;

    @Value("${mqtt.broker.persistence-dir}")
    private String persistenceDir;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence(persistenceDir);
        MqttClient client = new MqttClient(
                brokerUrl,
                clientId,
                persistence
        );
        MqttConnectOptions options = buildConnectOptions();
        client.connect(options);
        return client;
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        return buildConnectOptions();
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(autoReconnect);
        options.setCleanSession(cleanSession);
        options.setMaxInflight(maxInflight);
        return options;
    }

    public int getQos() {
        return qos;
    }
}
