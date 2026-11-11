package com.sqlexplan.exception;

import com.sqlexplan.common.ResultCode;
import lombok.Getter;

/**
 * 可预期的业务异常。
 */
@Getter
public class BizException extends RuntimeException {

  private final ResultCode resultCode;

  /**
   * 使用默认业务错误码构造异常。
   *
   * @param message 错误说明
   */
  public BizException(String message) {
    this(ResultCode.BIZ_ERROR, message);
  }

  /**
   * 指定响应码构造异常。
   *
   * @param resultCode 响应码
   * @param message    错误说明
   */
  public BizException(ResultCode resultCode, String message) {
    super(message);
    this.resultCode = resultCode;
  }
}
