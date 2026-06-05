package com.zc.iflyzcragback.service.rag.skill;

import com.zc.iflyzcragback.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherSkill implements Skill {
    public static final String NAME = "WeatherSkill";
    static final String ASK_CITY = "ASK_CITY";
    static final String ASK_DATE = "ASK_DATE";

    private final WeatherLookupService weatherLookupService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "天气查询";
    }

    @Override
    public String description() {
        return "通过多轮对话收集城市和日期，查询天气预报";
    }

    @Override
    public boolean canHandle(String input) {
        return input != null && (input.contains("查天气") || input.contains("查询天气") || input.contains("天气查询"));
    }

    @Override
    public SkillResult start(SkillContext context) {
        return SkillResult.ask("请告诉我你想查询哪个城市的天气？", ASK_CITY, context.mutableState());
    }

    @Override
    public SkillResult handle(String input, SkillContext context) {
        return switch (context.getCurrentStep()) {
            case ASK_CITY -> handleCity(input, context);
            case ASK_DATE -> handleDate(input, context);
            default -> SkillResult.done("天气技能状态异常，流程已结束。", context.mutableState());
        };
    }

    private SkillResult handleCity(String input, SkillContext context) {
        String city = normalize(input);
        if (city.isBlank()) {
            return SkillResult.ask("城市不能为空，请告诉我你想查询哪个城市。", ASK_CITY, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put("city", city);
        return SkillResult.ask("请告诉我你想查询哪一天的天气？（今天/明天/后天，或 yyyy-MM-dd）",
                ASK_DATE, state);
    }

    private SkillResult handleDate(String input, SkillContext context) {
        LocalDate date;
        try {
            date = parseDate(normalize(input));
        } catch (BizException e) {
            return SkillResult.ask(e.getMessage(), ASK_DATE, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put("date", date.toString());
        String city = String.valueOf(state.get("city"));
        try {
            return SkillResult.done(weatherLookupService.query(city, date), state);
        } catch (BizException e) {
            return SkillResult.done("天气查询失败：" + e.getMessage(), state);
        }
    }

    private LocalDate parseDate(String input) {
        LocalDate today = LocalDate.now();
        return switch (input) {
            case "今天" -> today;
            case "明天" -> today.plusDays(1);
            case "后天" -> today.plusDays(2);
            default -> {
                try {
                    LocalDate parsed = LocalDate.parse(input);
                    if (parsed.isBefore(today) || parsed.isAfter(today.plusDays(2))) {
                        throw new BizException("第一版天气查询仅支持今天、明天、后天或未来 3 天内的日期。");
                    }
                    yield parsed;
                } catch (DateTimeParseException e) {
                    throw new BizException("日期格式暂不支持，请输入今天、明天、后天或 yyyy-MM-dd。");
                }
            }
        };
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }
}
