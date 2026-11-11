package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 单条 SELECT 主体（不含 UNION 分支与顶层 CTE 列表）。
 */
@Data
public class SqlQueryBody {

  private List<TableReference> fromTables = new ArrayList<>();

  private List<JoinClause> joins = new ArrayList<>();

  private List<SelectColumnItem> selectColumns = new ArrayList<>();

  private List<WherePredicate> wherePredicates = new ArrayList<>();

  private List<String> groupByColumns = new ArrayList<>();

  private List<String> orderByColumns = new ArrayList<>();

  private List<Boolean> orderByAscFlags = new ArrayList<>();

  private Long limit;

  private Long offset;

  private List<AggregateFunctionItem> aggregateFunctions = new ArrayList<>();

  private List<SubqueryClause> subqueries = new ArrayList<>();
}
