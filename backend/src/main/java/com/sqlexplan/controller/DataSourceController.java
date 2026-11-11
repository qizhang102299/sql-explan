package com.sqlexplan.controller;

import com.sqlexplan.common.Result;
import com.sqlexplan.model.req.DataSourceDeleteReq;
import com.sqlexplan.model.req.DataSourceDetailReq;
import com.sqlexplan.model.req.DataSourcePageReq;
import com.sqlexplan.model.req.DataSourceSaveReq;
import com.sqlexplan.model.req.DataSourceTestReq;
import com.sqlexplan.model.res.DataSourcePageRes;
import com.sqlexplan.model.res.DataSourceRes;
import com.sqlexplan.model.res.DataSourceTestRes;
import com.sqlexplan.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目标数据源管理接口。
 */
@RestController
@RequestMapping("/api/datasource")
@Tag(name = "数据源管理")
public class DataSourceController {

  private final DataSourceService dataSourceService;

  public DataSourceController(DataSourceService dataSourceService) {
    this.dataSourceService = dataSourceService;
  }

  /**
   * 新增或更新数据源。
   *
   * @param req 保存请求
   * @return 数据源信息
   */
  @PostMapping("/save")
  @Operation(summary = "保存数据源")
  public Result<DataSourceRes> save(@Valid @RequestBody DataSourceSaveReq req) {
    return Result.ok(dataSourceService.save(req));
  }

  /**
   * 删除数据源。
   *
   * @param req 删除请求
   * @return 无业务数据
   */
  @PostMapping("/delete")
  @Operation(summary = "删除数据源")
  public Result<Void> delete(@Valid @RequestBody DataSourceDeleteReq req) {
    dataSourceService.delete(req);
    return Result.ok();
  }

  /**
   * 查询数据源详情。
   *
   * @param req 详情请求
   * @return 数据源信息
   */
  @PostMapping("/detail")
  @Operation(summary = "数据源详情")
  public Result<DataSourceRes> detail(@Valid @RequestBody DataSourceDetailReq req) {
    return Result.ok(dataSourceService.detail(req));
  }

  /**
   * 分页查询数据源列表。
   *
   * @param req 分页请求
   * @return 分页结果
   */
  @PostMapping("/page")
  @Operation(summary = "数据源分页")
  public Result<DataSourcePageRes> page(@Valid @RequestBody DataSourcePageReq req) {
    return Result.ok(dataSourceService.page(req));
  }

  /**
   * 测试数据源连通性。
   *
   * @param req 测试请求
   * @return 测试结果
   */
  @PostMapping("/test")
  @Operation(summary = "测试数据源连通性")
  public Result<DataSourceTestRes> test(@Valid @RequestBody DataSourceTestReq req) {
    return Result.ok(dataSourceService.testConnection(req));
  }
}
