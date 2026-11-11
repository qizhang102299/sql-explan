package com.sqlexplan.exception;

import com.sqlexplan.common.ResultCode;
import com.sqlexplan.model.res.SqlValidateRes;
import lombok.Getter;

/**
 * 可预期的业务异常。
 */
@Getter
public class BizException extends RuntimeException {

  private final ResultCode resultCode;

  private final SqlValidateRes validationDetail;

  /**
   * 使用默认业务错误码构造异常。
   *
   * @param message 错误说明
   */
  public BizException(String message) {
    this(ResultCode.BIZ_ERROR, message, null);
  }

  /**
   * 指定响应码构造异常。
   *
   * @param resultCode 响应码
   * @param message    错误说明
   */
  public BizException(ResultCode resultCode, String message) {
    this(resultCode, message, null);
  }

  /**
   * 携带 SQL 校验结构化信息的业务异常。
   *
   * @param resultCode       响应码
   * @param message          错误说明
   * @param validationDetail 校验详情
   */
  public BizException(ResultCode resultCode, String message, SqlValidateRes validationDetail) {
    super(message);
    this.resultCode = resultCode;
    this.validationDetail = validationDetail;
  }

  /**
   * 根据校验结果构造异常。
   *
   * @param detail 校验失败详情
   * @return 业务异常
   */
  public static BizException validationFailed(SqlValidateRes detail) {
    String message = detail.getMessage() != null ? detail.getMessage() : "SQL 校验未通过";
    return new BizException(ResultCode.BIZ_ERROR, message, detail);
  }
}
