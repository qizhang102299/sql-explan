package com.sqlexplan.exception;

import com.sqlexplan.common.Result;
import com.sqlexplan.common.ResultCode;
import com.sqlexplan.model.enums.SqlValidateErrorTypeEnum;
import com.sqlexplan.model.res.SqlValidateRes;
import com.sqlexplan.util.MySqlSemanticErrorUtil;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
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
  public Result<SqlValidateRes> handleBizException(BizException ex) {
    log.warn("业务异常: {}", ex.getMessage());
    if (ex.getValidationDetail() != null) {
      return Result.fail(ex.getResultCode(), ex.getMessage(), ex.getValidationDetail());
    }
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
  /**
   * 处理 JDBC 语法错误。
   *
   * @param ex 异常
   * @return 统一响应
   */
  @ExceptionHandler(BadSqlGrammarException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<SqlValidateRes> handleBadSqlGrammar(BadSqlGrammarException ex) {
    SqlValidateRes detail = mapSqlException(ex);
    return Result.fail(ResultCode.BIZ_ERROR, detail.getMessage(), detail);
  }

  /**
   * 处理目标库访问异常中的 SQL 语法/语义错误。
   *
   * @param ex 异常
   * @return 统一响应
   */
  @ExceptionHandler(DataAccessException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<SqlValidateRes> handleDataAccess(DataAccessException ex) {
    SQLException sqlEx = MySqlSemanticErrorUtil.findSqlException(ex);
    if (sqlEx == null || !isSqlValidationRelated(sqlEx)) {
      log.warn("数据访问异常: {}", ex.getMessage());
      return Result.fail(ResultCode.INTERNAL_ERROR);
    }
    SqlValidateRes detail = mapSqlException(ex);
    return Result.fail(ResultCode.BIZ_ERROR, detail.getMessage(), detail);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleException(Exception ex) {
    log.error("系统异常", ex);
    return Result.fail(ResultCode.INTERNAL_ERROR);
  }

  private static boolean isSqlValidationRelated(SQLException sqlEx) {
    return switch (sqlEx.getErrorCode()) {
      case 1064, 1146, 1054, 1052, 1305 -> true;
      default -> false;
    };
  }

  private SqlValidateRes mapSqlException(Throwable ex) {
    SQLException sqlEx = MySqlSemanticErrorUtil.findSqlException(ex);
    SqlValidateRes res = new SqlValidateRes();
    res.setValid(false);
    if (sqlEx == null) {
      res.setErrorType(SqlValidateErrorTypeEnum.SEMANTIC);
      res.setMessage(ex.getMessage() != null ? ex.getMessage() : "数据库执行失败");
      return res;
    }
    var mapped = MySqlSemanticErrorUtil.map(sqlEx, new ArrayList<>(), new ArrayList<>());
    res.setErrorType(sqlEx.getErrorCode() == 1064
        ? SqlValidateErrorTypeEnum.SYNTAX
        : SqlValidateErrorTypeEnum.SEMANTIC);
    res.setMessage(mapped.message());
    res.setMysqlErrorCode(mapped.mysqlErrorCode());
    res.setSuggestions(new ArrayList<>(mapped.suggestions()));
    return res;
  }
}
