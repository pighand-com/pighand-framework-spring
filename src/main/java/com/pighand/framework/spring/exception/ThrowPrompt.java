package com.pighand.framework.spring.exception;

import jakarta.servlet.http.HttpServletResponse;

import lombok.Data;

/**
 * 提示
 *
 * <p>用法：throw new ThrowPrompt()
 *
 * @author wangshuli
 */
@Data
public class ThrowPrompt extends RuntimeException implements ThrowInterface {

    /** 状态码 默认400 */
    private Integer code = HttpServletResponse.SC_BAD_REQUEST;
    /** 返回数据 */
    private Object data;

    /** 异常消息 */
    private String error;

    public ThrowPrompt(String error) {
        super(error);

        this.error = error;
    }

    public ThrowPrompt(String error, Object data) {
        super(error);

        this.error = error;
        this.data = data;
    }

    /**
     * @param error
     * @param code
     */
    public ThrowPrompt(String error, Integer code) {
        super(error);

        this.error = error;
        this.code = code;
    }

    /**
     * @param error
     * @param code
     */
    public ThrowPrompt(String error, Integer code, Object data) {
        super(error);

        this.error = error;
        this.code = code;
        this.data = data;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public String getError() {
        return error;
    }
}
