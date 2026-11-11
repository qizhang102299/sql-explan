package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.SqlSetOperationEnum;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * SQL 解析后的语义结构（支持 CTE、UNION、子查询嵌套）。
 */
@Data
public class SqlStructure {

  /**
   * WITH 子句中的 CTE 列表。
   */
  private List<CteClause> ctes = new ArrayList<>();

  /**
   * 集合运算类型；单条 SELECT 时为 null。
   */
  private SqlSetOperationEnum setOperation;

  /**
   * UNION 等集合运算的各分支（递归结构）。
   */
  private List<SqlStructure> unionBranches = new ArrayList<>();

  /**
   * 单条查询主体（非 UNION 顶层时使用）。
   */
  private SqlQueryBody body;

  /**
   * 全查询树中出现的物理表（去重汇总）。
   */
  private List<TableReference> allTables = new ArrayList<>();
}
