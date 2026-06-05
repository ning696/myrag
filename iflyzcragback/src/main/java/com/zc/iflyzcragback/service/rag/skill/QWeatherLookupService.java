package com.zc.iflyzcragback.service.rag.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.config.WeatherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class QWeatherLookupService implements WeatherLookupService {
    private final WeatherProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public String query(String city, LocalDate date) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BizException("天气服务未配置 WEATHER_API_KEY");
        }
        if (props.getApiHost() == null || props.getApiHost().isBlank()) {
            throw new BizException("天气服务未配置 WEATHER_API_HOST");
        }
        try {
            String locationId = lookupLocation(city);
            JsonNode daily = fetchDailyWeather(locationId);
            for (JsonNode day : daily) {
                LocalDate fxDate = LocalDate.parse(day.path("fxDate").asText());
                if (fxDate.equals(date)) {
                    return "%s %s 天气：%s，气温 %s-%s℃，%s，风力 %s 级。".formatted(
                            city,
                            fxDate,
                            day.path("textDay").asText("未知"),
                            day.path("tempMin").asText("-"),
                            day.path("tempMax").asText("-"),
                            day.path("windDirDay").asText("风向未知"),
                            day.path("windScaleDay").asText("-"));
                }
            }
            throw new BizException("天气服务暂不支持该日期");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("天气查询失败: " + e.getMessage());
        }
    }

    private String lookupLocation(String city) throws Exception {
        String endpoint = apiUrl("/geo/v2/city/lookup");
        String url = endpoint + "?location=" + encode(city);
        log.debug("QWeather city lookup | endpoint={} | city={}", endpoint, city);
        JsonNode root = getJson(url);
        ensureQWeatherOk(root);
        JsonNode locations = root.path("location");
        if (!locations.isArray() || locations.isEmpty()) {
            throw new BizException("未找到城市：" + city);
        }
        return locations.get(0).path("id").asText();
    }

    private JsonNode fetchDailyWeather(String locationId) throws Exception {
        String endpoint = apiUrl("/v7/weather/3d");
        String url = endpoint + "?location=" + encode(locationId);
        log.debug("QWeather daily forecast | endpoint={} | locationId={}", endpoint, locationId);
        JsonNode root = getJson(url);
        ensureQWeatherOk(root);
        return root.path("daily");
    }

    private JsonNode getJson(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeout()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(props.getTimeout()))
                .header("X-QW-Api-Key", props.getApiKey())
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("天气服务 HTTP " + response.statusCode() + "，请检查 WEATHER_API_KEY 与 WEATHER_API_HOST 是否匹配");
        }
        byte[] body = decodeResponseBody(response);
        return objectMapper.readTree(body);
    }

    private void ensureQWeatherOk(JsonNode root) {
        String code = root.path("code").asText();
        if (!"200".equals(code)) {
            throw new BizException("天气服务返回异常 code=" + code);
        }
    }

    private String apiUrl(String path) {
        String apiHost = props.getApiHost().trim();
        if (!apiHost.startsWith("http://") && !apiHost.startsWith("https://")) {
            apiHost = "https://" + apiHost;
        }
        if (apiHost.endsWith("/")) {
            apiHost = apiHost.substring(0, apiHost.length() - 1);
        }
        return apiHost + path;
    }

    private byte[] decodeResponseBody(HttpResponse<byte[]> response) throws Exception {
        byte[] body = response.body();
        String encoding = response.headers()
                .firstValue("Content-Encoding")
                .orElse("")
                .toLowerCase(Locale.ROOT);
        if (encoding.contains("gzip") || isGzip(body)) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                gzip.transferTo(output);
                return output.toByteArray();
            }
        }
        return body;
    }

    private boolean isGzip(byte[] body) {
        return body != null
                && body.length >= 2
                && (body[0] & 0xff) == 0x1f
                && (body[1] & 0xff) == 0x8b;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
