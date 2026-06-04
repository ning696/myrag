package com.zc.iflyzcragback.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 统一接口响应结构。
 *
 * <p>前端收到的 JSON 都尽量保持 code/message/data 三段式：
 * code 表示业务状态，message 给用户或开发者提示，data 承载真正的数据。</p>
 */
public class Result<T> {

    /** 业务状态码，0 表示成功。 */
    private int code;
    /** 响应提示信息。 */
    private String message;
    /** 响应数据。 */
    private T data;

    /**
     * 成功响应，并携带数据。
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "ok", data);
    }

    /**
     * 成功响应，不携带数据。
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "ok", null);
    }

    /**
     * 失败响应。
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
