package com.sqlexplan.controller;

import com.sqlexplan.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口，用于验证服务与统一响应体。
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "健康检查")
public class HealthController {

  /**
   * 返回服务存活状态。
   *
   * @return 统一响应，data 为 UP
   */
  @GetMapping
  @Operation(summary = "健康检查")
  public Result<String> health() {
    return Result.ok("UP");
  }
}
