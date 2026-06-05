package com.zc.iflyzcragback.service.rag.skill;

import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.config.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class SmtpEmailDeliveryService implements EmailDeliveryService {
    private final MailProperties props;

    @Override
    public void send(String recipient, String subject, String content) {
        validateConfig();
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(props.getHost());
            sender.setPort(props.getPort());
            sender.setUsername(props.getUsername());
            sender.setPassword(props.getPassword());
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties javaMailProps = sender.getJavaMailProperties();
            javaMailProps.put("mail.smtp.auth", "true");
            javaMailProps.put("mail.smtp.ssl.enable", String.valueOf(props.isSslEnable()));
            javaMailProps.put("mail.smtp.starttls.enable", String.valueOf(!props.isSslEnable()));

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.getFrom());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(content);
            sender.send(message);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("邮件发送失败: " + e.getMessage());
        }
    }

    private void validateConfig() {
        if (blank(props.getHost()) || blank(props.getUsername()) || blank(props.getPassword()) || blank(props.getFrom())) {
            throw new BizException("邮件服务未配置完整，请检查 SMTP_HOST、SMTP_USERNAME、SMTP_PASSWORD、SMTP_FROM");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
