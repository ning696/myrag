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
/**
 * SMTP 邮件发送实现。
 *
 * <p>这是邮件技能里真正产生外部副作用的类。调用方必须在进入这里之前完成收件人、主题、
 * 正文收集，并取得用户明确确认。</p>
 *
 * <p>SMTP 密码、发件人等配置来自 {@link MailProperties}，不应写入数据库、前端表单或日志。</p>
 */
public class SmtpEmailDeliveryService implements EmailDeliveryService {
    private final MailProperties props;

    @Override
    /**
     * 发送纯文本邮件。
     */
    public void send(String recipient, String subject, String content) {
        validateConfig();
        try {
            // 每次发送按当前配置创建 sender，避免配置变更后复用旧连接状态。
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
            javaMailProps.put("mail.smtp.connectiontimeout", String.valueOf(props.getConnectionTimeout()));
            javaMailProps.put("mail.smtp.timeout", String.valueOf(props.getTimeout()));
            javaMailProps.put("mail.smtp.writetimeout", String.valueOf(props.getWriteTimeout()));
            javaMailProps.put("mail.debug", String.valueOf(props.isDebug()));
            if (props.isSslEnable() && !blank(props.getSslProtocols())) {
                javaMailProps.put("mail.smtp.ssl.protocols", props.getSslProtocols());
            }

            // 当前技能发送纯文本邮件，后续如需 HTML/附件再改用 MimeMessage。
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

    /**
     * 在发送前校验必要 SMTP 配置，缺失时返回业务异常而不是底层连接错误。
     */
    private void validateConfig() {
        if (blank(props.getHost()) || blank(props.getUsername()) || blank(props.getPassword()) || blank(props.getFrom())) {
            throw new BizException("邮件服务未配置完整，请检查 SMTP_HOST、SMTP_USERNAME、SMTP_PASSWORD、SMTP_FROM");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
