package com.sqlexplan.exception;

import com.sqlexplan.common.Result;
import com.sqlexplan.common.ResultCode;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，将异常转换为统一响应体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 处理业务异常。
   *
   * @param ex 业务异常
   * @return 统一响应
   */
  @ExceptionHandler(BizException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleBizException(BizException ex) {
    log.warn("业务异常: {}", ex.getMessage());
    return Result.fail(ex.getResultCode(), ex.getMessage());
  }

  /**
   * 处理 @RequestBody 参数校验异常。
   *
   * @param ex 校验异常
   * @return 统一响应
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining("; "));
    log.warn("参数校验失败: {}", message);
    return Result.fail(ResultCode.BAD_REQUEST, message);
  }

  /**
   * 处理表单绑定校验异常。
   *
   * @param ex 绑定异常
   * @return 统一响应
   */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleBindException(BindException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining("; "));
    log.warn("参数绑定失败: {}", message);
    return Result.fail(ResultCode.BAD_REQUEST, message);
  }

  /**
   * 处理 @RequestParam 等约束校验异常。
   *
   * @param ex 约束违反异常
   * @return 统一响应
   */
  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
        .collect(Collectors.joining("; "));
    log.warn("约束校验失败: {}", message);
    return Result.fail(ResultCode.BAD_REQUEST, message);
  }

  /**
   * 处理未捕获异常。
   *
   * @param ex 异常
   * @return 统一响应
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleException(Exception ex) {
    log.error("系统异常", ex);
    return Result.fail(ResultCode.INTERNAL_ERROR);
  }
}
