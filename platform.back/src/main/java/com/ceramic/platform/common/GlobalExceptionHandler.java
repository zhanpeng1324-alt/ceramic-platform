package com.ceramic.platform.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class
GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleBadRequest(IllegalArgumentException ex) {
        return Result.error(400, ex.getMessage());
    }

    /**
     * 静态资源不存在（图片缺失、路径写错）：返回标准 404 而非 500 堆栈。
     * 正常业务波动（如商品图被删），只记 debug 不刷 ERROR 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return Result.error(404, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
        log.error("Unhandled server exception", ex);
        return Result.error(500, "服务器暂时无法处理请求，请稍后重试");
    }
}
