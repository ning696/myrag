package com.zc.iflyzcragback.service.rag.skill;

public interface EmailDeliveryService {
    void send(String recipient, String subject, String content);
}
