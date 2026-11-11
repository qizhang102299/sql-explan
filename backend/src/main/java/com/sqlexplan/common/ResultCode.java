package com.sqlexplan.common;

import lombok.Getter;

/**
 * 统一业务响应码。
 */
@Getter
public enum ResultCode {

  SUCCESS(0, "成功"),
  BAD_REQUEST(400, "请求参数错误"),
  BIZ_ERROR(1000, "业务处理失败"),
  INTERNAL_ERROR(500, "系统内部错误");

  private final int code;

  private final String defaultMessage;

  ResultCode(int code, String defaultMessage) {
    this.code = code;
    this.defaultMessage = defaultMessage;
  }
}
