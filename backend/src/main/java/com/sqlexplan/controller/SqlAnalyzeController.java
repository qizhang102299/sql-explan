package com.sqlexplan.controller;

import com.sqlexplan.common.Result;
import com.sqlexplan.model.req.SqlAnalyzeReq;
import com.sqlexplan.model.req.SqlHistoryDetailReq;
import com.sqlexplan.model.req.SqlHistoryPageReq;
import com.sqlexplan.model.req.SqlValidateReq;
import com.sqlexplan.model.res.SqlAnalyzeRes;
import com.sqlexplan.model.res.SqlHistoryPageRes;
import com.sqlexplan.model.res.SqlValidateRes;
import com.sqlexplan.service.SqlAnalyzeService;
import com.sqlexplan.service.SqlValidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL 分析与历史接口。
 */
@RestController
@RequestMapping("/api/sql")
@Tag(name = "SQL 分析")
public class SqlAnalyzeController {

  private final SqlAnalyzeService sqlAnalyzeService;

  private final SqlValidateService sqlValidateService;

  public SqlAnalyzeController(SqlAnalyzeService sqlAnalyzeService, SqlValidateService sqlValidateService) {
    this.sqlAnalyzeService = sqlAnalyzeService;
    this.sqlValidateService = sqlValidateService;
  }

  /**
   * 综合分析 SQL。
   *
   * @param req 分析请求
   * @return 分析结果
   */
  @PostMapping("/analyze")
  @Operation(summary = "SQL 综合分析")
  public Result<SqlAnalyzeRes> analyze(@Valid @RequestBody SqlAnalyzeReq req) {
    return Result.ok(sqlAnalyzeService.analyze(req));
  }

  /**
   * 校验 SQL 语法、安全与可选语义（EXPLAIN）。
   *
   * @param req 校验请求
   * @return 校验结果
   */
  @PostMapping("/validate")
  @Operation(summary = "SQL 语法校验")
  public Result<SqlValidateRes> validate(@Valid @RequestBody SqlValidateReq req) {
    return Result.ok(sqlValidateService.validate(req));
  }

  /**
   * 分页查询分析历史。
   *
   * @param req 分页请求
   * @return 分页结果
   */
  @PostMapping("/history/page")
  @Operation(summary = "分析历史分页")
  public Result<SqlHistoryPageRes> historyPage(@Valid @RequestBody SqlHistoryPageReq req) {
    return Result.ok(sqlAnalyzeService.historyPage(req));
  }

  /**
   * 分析历史详情。
   *
   * @param req 详情请求
   * @return 完整分析结果
   */
  @PostMapping("/history/detail")
  @Operation(summary = "分析历史详情")
  public Result<SqlAnalyzeRes> historyDetail(@Valid @RequestBody SqlHistoryDetailReq req) {
    return Result.ok(sqlAnalyzeService.historyDetail(req));
  }
}
