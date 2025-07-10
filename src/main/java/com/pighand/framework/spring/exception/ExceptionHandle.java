package com.pighand.framework.spring.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pighand.framework.spring.PighandFrameworkConfig;
import com.pighand.framework.spring.response.Result;
import com.pighand.framework.spring.util.VerifyUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 异常处理
 *
 * @author wangshuli
 */
@Slf4j
@ControllerAdvice
public class ExceptionHandle {

    public static final MediaType APPLICATION_JSON_UTF8 =
        new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(),
            StandardCharsets.UTF_8);

    // 异常返回数据方法
    private static final Map<String, Function<Object, Object>> exceptionDataFunction = new HashMap<>();

    private static final Set<String> exceptionDataFunctionNames = new HashSet<>();

    ObjectMapper objectMapper = new ObjectMapper();

    @Value("${access-control-allow.origin}")
    private String AccessControlAllowOrigin;

    @Value("${access-control-allow.methods}")
    private String AccessControlAllowMethods;

    @Value("${access-control-allow.headers}")
    private String AccessControlAllowHeaders;

    /**
     * 设置异常返回数据方法
     *
     * @param name
     * @param function
     */
    public static void PutExceptionDataFunction(String name, Function<Object, Object> function) {
        exceptionDataFunction.put(name, function);
        exceptionDataFunctionNames.add(name);
    }

    /**
     * 设置response异常
     *
     * @param request
     * @param response
     * @param ex
     * @throws IOException
     */
    public void setResponse(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        Object result = this.javaException(request, response, ex);

        response.setHeader("Access-Control-Allow-Origin", AccessControlAllowOrigin);
        response.setHeader("Access-Control-Allow-Methods", AccessControlAllowMethods);
        response.setHeader("Access-Control-Allow-Headers", AccessControlAllowHeaders);

        response.setContentType(APPLICATION_JSON_UTF8.toString());
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Object javaException(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        if (ex instanceof NoHandlerFoundException) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return ex.getMessage();
        }

        if (ex instanceof MethodArgumentNotValidException validException) {
            // 处理@Validated异常
            FieldError fieldError = validException.getFieldError();

            String validMessage = fieldError.getField() + " " + fieldError.getDefaultMessage();

            return getExceptionResult(ex.getMessage(), new ThrowPrompt(validMessage), ExceptionEnum.PROMPT, response,
                request);
        } else if (ex instanceof ConstraintViolationException validException) {
            // url参数、数组类参数校验报错类

            return getExceptionResult(ex.getMessage(), new ThrowPrompt(validException.getMessage()),
                ExceptionEnum.PROMPT, response, request);
        } else if (PighandFrameworkConfig.exception.isInterceptException()) {
            // 拦截系统异常
            String error = ex.getMessage();

            if (ex instanceof WebClientResponseException) {
                error += ", body: " + ((WebClientResponseException.BadRequest)ex).getResponseBodyAsString();
            }

            return getExceptionResult(error, ex, ExceptionEnum.EXCEPTION, response, request);
        } else {
            // 其他系统异常，直接返回
            this.privateErrorStack(null, ex.getMessage(), ex.getStackTrace(), ExceptionEnum.EXCEPTION);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ex.getMessage();
        }
    }

    /**
     * 异常返回值
     *
     * @param responseError
     * @param ex
     * @param type
     * @param response
     * @param request
     * @return
     */
    private Result getExceptionResult(String responseError, Exception ex, ExceptionEnum type,
        HttpServletResponse response, HttpServletRequest request) {

        Integer code = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

        // 错误返回数据。如果异常方法已有返回数据，则合并
        Object data = "";
        for (String exceptionDataFunctionName : exceptionDataFunctionNames) {
            Object functionParam = request.getAttribute(exceptionDataFunctionName);
            if (functionParam != null) {
                data = exceptionDataFunction.get(exceptionDataFunctionName).apply(functionParam);
            }
        }

        if (ex instanceof ThrowInterface throwInfo) {
            code = throwInfo.getCode();
            Object throwInfoData = throwInfo.getData();

            // 合并返回数据
            if (throwInfoData != null) {
                ObjectNode dataJson =
                    VerifyUtils.isNotEmpty(data) ? objectMapper.valueToTree(data) : objectMapper.createObjectNode();

                if (throwInfoData instanceof String) {
                    dataJson.put("data", (String)throwInfoData);
                } else {
                    dataJson.setAll((ObjectNode)objectMapper.valueToTree(throwInfoData));
                }

                data = dataJson;
            }

            if (VerifyUtils.isEmpty(responseError)) {
                responseError = throwInfo.getError();
            }
        } else if (VerifyUtils.isEmpty(responseError)) {
            responseError = ex.getMessage();
        }

        if (type.equals(ExceptionEnum.EXCEPTION)) {
            String overallErrorMessage = PighandFrameworkConfig.exception.getMessage();

            if (StringUtils.hasText(overallErrorMessage)) {
                responseError = overallErrorMessage;
            }
        }

        // 处理空指针
        if (VerifyUtils.isEmpty(responseError)) {
            responseError = "java.lang.NullPointerException";
        }

        // 设置HTTP状态码
        response.setStatus(HttpServletResponse.SC_OK);

        // 打印日志
        Boolean isLog = type.equals(ExceptionEnum.EXCEPTION);
        if (isLog) {
            this.privateErrorStack(code, responseError, ex.getStackTrace(), type);
        }

        return new Result(code, data, responseError);
    }

    /**
     * 错误处理
     *
     * @param request  request
     * @param response response
     * @param ex       ex
     * @return Result
     */
    @ExceptionHandler(ThrowPrompt.class)
    @ResponseBody
    public Result prompt(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        return this.getExceptionResult(ex.getMessage(), ex, ExceptionEnum.PROMPT, response, request);
    }

    /**
     * 异常处理
     *
     * @param request  request
     * @param response response
     * @param ex       ex
     * @return Result
     */
    @ExceptionHandler(ThrowException.class)
    @ResponseBody
    public Result exception(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        return this.getExceptionResult(ex.getMessage(), ex, ExceptionEnum.EXCEPTION, response, request);
    }

    /**
     * 打印异常日志
     *
     * @param code    error code
     * @param message error message
     * @param stacks  exception stacks
     */
    private void privateErrorStack(Integer code, String message, StackTraceElement[] stacks,
        ExceptionEnum exceptionEnum) {
        StringBuilder exMsg = new StringBuilder();

        if (VerifyUtils.isNotEmpty(code)) {
            exMsg.append("\nCode:").append(code);
        }

        exMsg.append("\n").append("Exception:\n\t").append(message).append("\n").append("Stacks:\n");

        for (StackTraceElement stack : stacks) {
            exMsg.append("\t").append(stack).append("\n");
        }

        if (ExceptionEnum.PROMPT.equals(exceptionEnum)) {
            log.warn(exMsg.toString());
        } else if (ExceptionEnum.EXCEPTION.equals(exceptionEnum)) {
            log.error(exMsg.toString());
        }
    }
}
