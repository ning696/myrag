package com.zc.iflyzcragback.service.rag.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentTimeTool implements ManagedTool {

    public static final String NAME = "current_time";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CHINESE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss");

    private final RagProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "当前时间";
    }

    @Override
    public String description() {
        return "获取当前日期、时间、星期和 ISO 时间戳。";
    }

    @Tool(name = NAME, value = "获取当前日期、时间、星期和 ISO 时间戳。遇到今天、当前、现在、最新等相对时间时先使用。无参数。")
    public String currentTime() {
        try {
            TimeInfo info = currentTime(zone());
            log.info("Current time tool finished | timezone={} | date={} | time={} | weekday={} | isoTimestamp={}",
                    info.timezone(), info.date(), info.time(), info.weekday(), info.isoTimestamp());
            return objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "timezone", info.timezone(),
                    "date", info.date(),
                    "time", info.time(),
                    "weekday", info.weekday(),
                    "isoTimestamp", info.isoTimestamp(),
                    "formatted", info.formatted()
            ));
        } catch (Exception e) {
            log.warn("Current time tool failed", e);
            return "{\"success\":false,\"message\":\"时间工具执行失败\"}";
        }
    }

    TimeInfo currentTime(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        String weekday = now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA);
        String formatted = "当前时间：" + now.format(CHINESE_FORMAT) + "，" + weekday + "，" + zone.getId();
        return new TimeInfo(
                zone.getId(),
                now.format(DATE_FORMAT),
                now.format(TIME_FORMAT),
                weekday,
                now.toOffsetDateTime().toString(),
                formatted
        );
    }

    private ZoneId zone() {
        String zoneId = props.getTools().getTime().getDefaultZone();
        try {
            return ZoneId.of(zoneId == null || zoneId.isBlank() ? "Asia/Shanghai" : zoneId);
        } catch (DateTimeException e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    public record TimeInfo(String timezone,
                           String date,
                           String time,
                           String weekday,
                           String isoTimestamp,
                           String formatted) {
    }
}
