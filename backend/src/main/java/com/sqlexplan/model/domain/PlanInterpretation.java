package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 执行计划规则解读汇总。
 */
@Data
public class PlanInterpretation {

  private List<PlanIssue> issues = new ArrayList<>();

  private String summary;
}
