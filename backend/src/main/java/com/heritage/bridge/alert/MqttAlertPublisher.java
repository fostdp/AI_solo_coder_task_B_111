package com.heritage.bridge.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.bridge.config.MqttConfig;
import com.heritage.bridge.entity.Alert;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttAlertPublisher {

    private final MqttClient mqttClient;
    private final MqttConfig mqttConfig;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topic.alert:heritage/bridge/alert}")
    private String alertTopic;

    private final BlockingQueue<Alert> pendingQueue = new LinkedBlockingQueue<>(1000);
    private volatile boolean running = true;
    private Thread publisherThread;

    @jakarta.annotation.PostConstruct
    public void init() {
        running = true;
        publisherThread = new Thread(this::drainQueueLoop, "mqtt-alert-publisher");
        publisherThread.setDaemon(true);
        publisherThread.start();
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT连接{}完成 serverURI={}", reconnect ? "重连" : "首次", serverURI);
                if (reconnect) flushQueued();
            }
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT连接丢失: {}", cause.getMessage());
            }
            @Override
            public void messageArrived(String topic, MqttMessage message) {}
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (publisherThread != null) publisherThread.interrupt();
    }

    public void publish(Alert alert) {
        if (alert == null) return;
        boolean ok = pendingQueue.offer(alert);
        if (!ok) log.error("告警队列已满,丢弃告警 bridgeId={}, msg={}", alert.getBridgeId(), alert.getMessage());
    }

    private void drainQueueLoop() {
        while (running) {
            try {
                Alert a = pendingQueue.poll(1, TimeUnit.SECONDS);
                if (a != null) publishWithRetry(a);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("告警发布异常: {}", e.getMessage());
            }
        }
    }

    private void flushQueued() {
        int size = pendingQueue.size();
        if (size > 0) log.info("重连后重发 {} 条排队告警", size);
    }

    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void publishWithRetry(Alert alert) {
        if (!mqttClient.isConnected()) {
            log.warn("MQTT未连接,等待自动重连...将告警重新入队");
            pendingQueue.offer(alert);
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", alert.getId());
            payload.put("bridgeId", alert.getBridgeId());
            payload.put("sensorId", alert.getSensorId());
            payload.put("type", alert.getType());
            payload.put("level", alert.getLevel());
            payload.put("message", alert.getMessage());
            payload.put("value", alert.getValue());
            payload.put("threshold", alert.getThreshold());
            payload.put("timestamp", alert.getTimestamp() == null ? null : alert.getTimestamp().toString());
            payload.put("acknowledged", alert.getAcknowledged());
            payload.put("eventSource", "bridge-monitor");

            String json = objectMapper.writeValueAsString(payload);
            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(Math.min(2, Math.max(0, mqttConfig.getQos())));
            msg.setRetained(false);

            mqttClient.publish(alertTopic, msg);
            log.info("MQTT告警已推送 QoS={} topic={}, level={}, bridge={}, msg={}",
                    msg.getQos(), alertTopic, alert.getLevel(), alert.getBridgeId(), alert.getMessage());
        } catch (MqttException me) {
            log.error("MQTT告警推送失败,等待重试: {}", me.getMessage());
            pendingQueue.offer(alert);
            throw new RuntimeException("mqtt push failed", me);
        } catch (Exception e) {
            log.error("MQTT告警推送异常: {}", e.getMessage());
            pendingQueue.offer(alert);
            throw new RuntimeException("mqtt payload failed", e);
        }
    }
}
