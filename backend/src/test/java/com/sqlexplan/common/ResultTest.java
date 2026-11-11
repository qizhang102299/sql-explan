package com.sqlexplan.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link Result} 单元测试。
 */
class ResultTest {

  @Test
  void okShouldUseSuccessCodeAndTimestamp() {
    Result<String> result = Result.ok("data");

    assertThat(result.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
    assertThat(result.getMessage()).isEqualTo(ResultCode.SUCCESS.getDefaultMessage());
    assertThat(result.getData()).isEqualTo("data");
    assertThat(result.getTimestamp()).isNotNull();
    assertThat(result.getTimestamp()).isPositive();
  }

  @Test
  void failShouldClearData() {
    Result<Void> result = Result.fail(ResultCode.BAD_REQUEST, "参数错误");

    assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
    assertThat(result.getMessage()).isEqualTo("参数错误");
    assertThat(result.getData()).isNull();
    assertThat(result.getTimestamp()).isNotNull();
  }

  @Test
  void bizFailShouldUseBizErrorCode() {
    Result<Void> result = Result.bizFail("业务失败");

    assertThat(result.getCode()).isEqualTo(ResultCode.BIZ_ERROR.getCode());
    assertThat(result.getMessage()).isEqualTo("业务失败");
  }
}
