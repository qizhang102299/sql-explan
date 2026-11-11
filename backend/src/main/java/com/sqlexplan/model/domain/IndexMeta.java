package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 表索引元数据。
 */
@Data
public class IndexMeta {

  private String indexName;

  private Boolean unique;

  private String indexType;

  private List<IndexColumnMeta> columns = new ArrayList<>();
}
