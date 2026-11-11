package com.sqlexplan.controller;

import com.sqlexplan.common.Result;
import com.sqlexplan.model.req.DbObjectListReq;
import com.sqlexplan.model.req.DbObjectRoutineDetailReq;
import com.sqlexplan.model.req.DbObjectTableDetailReq;
import com.sqlexplan.model.req.DbObjectViewDetailReq;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import com.sqlexplan.service.DbObjectBrowserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目标库对象浏览器接口（只读元数据）。
 */
@RestController
@RequestMapping("/api/datasource/object")
@Tag(name = "数据源对象浏览器")
public class DbObjectController {

  private final DbObjectBrowserService dbObjectBrowserService;

  public DbObjectController(DbObjectBrowserService dbObjectBrowserService) {
    this.dbObjectBrowserService = dbObjectBrowserService;
  }

  /**
   * 列举表、视图与例程。
   *
   * @param req 列表请求
   * @return 对象列表
   */
  @PostMapping("/list")
  @Operation(summary = "对象列表")
  public Result<DbObjectListRes> list(@Valid @RequestBody DbObjectListReq req) {
    return Result.ok(dbObjectBrowserService.listObjects(req));
  }

  /**
   * 表结构详情。
   *
   * @param req 表详情请求
   * @return 表详情
   */
  @PostMapping("/table-detail")
  @Operation(summary = "表结构详情")
  public Result<DbTableDetailRes> tableDetail(@Valid @RequestBody DbObjectTableDetailReq req) {
    return Result.ok(dbObjectBrowserService.getTableDetail(req));
  }

  /**
   * 视图详情。
   *
   * @param req 视图详情请求
   * @return 视图详情
   */
  @PostMapping("/view-detail")
  @Operation(summary = "视图详情")
  public Result<DbViewDetailRes> viewDetail(@Valid @RequestBody DbObjectViewDetailReq req) {
    return Result.ok(dbObjectBrowserService.getViewDetail(req));
  }

  /**
   * 存储过程/函数详情。
   *
   * @param req 例程详情请求
   * @return 例程详情
   */
  @PostMapping("/routine-detail")
  @Operation(summary = "例程详情")
  public Result<DbRoutineDetailRes> routineDetail(@Valid @RequestBody DbObjectRoutineDetailReq req) {
    return Result.ok(dbObjectBrowserService.getRoutineDetail(req));
  }
}
