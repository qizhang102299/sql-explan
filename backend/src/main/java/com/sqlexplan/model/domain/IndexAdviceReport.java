package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 索引建议汇总。
 */
@Data
public class IndexAdviceReport {

  private List<IndexAdvice> advices = new ArrayList<>();

  private String summary;
}
