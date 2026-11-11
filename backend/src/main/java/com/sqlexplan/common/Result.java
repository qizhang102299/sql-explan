package com.sqlexplan.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一 HTTP 响应体。
 *
 * @param <T> 业务数据类型
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "统一响应体")
public class Result<T> {

  @Schema(description = "业务码，0 表示成功")
  private Integer code;

  @Schema(description = "提示信息")
  private String message;

  @Schema(description = "业务数据")
  private T data;

  @Schema(description = "响应时间戳（毫秒）")
  private Long timestamp;

  /**
   * 成功响应（无业务数据）。
   *
   * @param <T> 数据类型占位
   * @return 成功结果
   */
  public static <T> Result<T> ok() {
    return ok(null);
  }

  /**
   * 成功响应。
   *
   * @param data 业务数据
   * @param <T>  数据类型
   * @return 成功结果
   */
  public static <T> Result<T> ok(T data) {
    return new Result<>(
        ResultCode.SUCCESS.getCode(),
        ResultCode.SUCCESS.getDefaultMessage(),
        data,
        System.currentTimeMillis());
  }

  /**
   * 失败响应（使用枚举默认文案）。
   *
   * @param resultCode 响应码枚举
   * @param <T>        数据类型
   * @return 失败结果
   */
  public static <T> Result<T> fail(ResultCode resultCode) {
    return fail(resultCode, resultCode.getDefaultMessage());
  }

  /**
   * 失败响应（自定义文案）。
   *
   * @param resultCode 响应码枚举
   * @param message    提示信息
   * @param <T>        数据类型
   * @return 失败结果
   */
  public static <T> Result<T> fail(ResultCode resultCode, String message) {
    return fail(resultCode, message, null);
  }

  /**
   * 失败响应（自定义文案与附带数据，如校验详情）。
   *
   * @param resultCode 响应码枚举
   * @param message    提示信息
   * @param data       附带数据
   * @param <T>        数据类型
   * @return 失败结果
   */
  public static <T> Result<T> fail(ResultCode resultCode, String message, T data) {
    return new Result<>(resultCode.getCode(), message, data, System.currentTimeMillis());
  }

  /**
   * 业务失败响应。
   *
   * @param message 提示信息
   * @param <T>     数据类型
   * @return 失败结果
   */
  public static <T> Result<T> bizFail(String message) {
    return fail(ResultCode.BIZ_ERROR, message);
  }
}
