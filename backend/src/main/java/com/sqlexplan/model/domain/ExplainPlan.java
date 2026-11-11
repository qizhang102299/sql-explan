package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 执行 EXPLAIN FORMAT=JSON 后的结构化结果。
 */
@Data
public class ExplainPlan {

  private PlanNode root;

  private List<String> parseWarnings = new ArrayList<>();
}
