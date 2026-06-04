package com.zc.iflyzcragback.common;

import lombok.Getter;

@Getter
/**
 * 业务异常。
 *
 * <p>用于表达“用户参数不对、资源不存在、权限不符合”等可预期问题。
 * GlobalExceptionHandler 会把它转换成统一 JSON 响应。</p>
 */
public class BizException extends RuntimeException {

    /** 业务错误码，默认 400。 */
    private final int code;

    /**
     * 指定错误码和错误信息。
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用默认 400 错误码。
     */
    public BizException(String message) {
        super(message);
        this.code = 400;
    }
}
