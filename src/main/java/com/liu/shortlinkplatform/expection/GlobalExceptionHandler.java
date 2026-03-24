package com.liu.shortlinkplatform.expection;

import com.liu.shortlinkplatform.common.Result;
import com.liu.shortlinkplatform.enums.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.validation.BindException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 捕获业务异常（自定义）
     */
    @ExceptionHandler(BusinessException.class)
    public Result <?> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 捕获参数校验异常
     */
    @ExceptionHandler({BindException.class , MethodArgumentNotValidException.class})
    public Result <?> handleParamException(Exception e) {
        String message = "参数校验异常";
        if (e instanceof BindException) {
            message = ((BindException) e).getFieldError().getDefaultMessage();
        } else if (e instanceof MethodArgumentNotValidException){
            message = ((MethodArgumentNotValidException) e).getFieldError().getDefaultMessage();
        }
        log.error("参数异常：{}", e.getMessage(), e);
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 捕获系统异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleSystemException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        return Result.fail(ResultCodeEnum.FAIL.getCode(), "系统异常");
    }

}
