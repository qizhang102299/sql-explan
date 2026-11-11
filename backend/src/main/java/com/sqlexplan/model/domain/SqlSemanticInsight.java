package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * SQL 语义分析（意图、过滤、连接、风险等）。
 */
@Data
public class SqlSemanticInsight {

  private String intentSummary;

  private List<String> involvedTables = new ArrayList<>();

  private String filtersDescription;

  private String joinsDescription;

  private String aggregationDescription;

  private String orderingDescription;

  /**
   * LIMIT / OFFSET 分页说明。
   */
  private String paginationDescription;

  /**
   * 子查询与 CTE、UNION 等结构说明。
   */
  private String subqueryCteDescription;

  private List<String> riskNotes = new ArrayList<>();
}
