package com.sqlexplan.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.TableSchemaMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SchemaMetaVersionUtil} 单元测试。
 */
class SchemaMetaVersionUtilTest {

  @Test
  void versionChangesWhenColumnAdded() {
    TableSchemaMeta t1 = tableWithColumn("id");
    TableSchemaMeta t2 = tableWithColumn("id", "name");
    String v1 = SchemaMetaVersionUtil.computeVersion(List.of(t1));
    String v2 = SchemaMetaVersionUtil.computeVersion(List.of(t2));
    assertThat(v1).isNotEqualTo(v2);
  }

  @Test
  void versionStableForSameSchema() {
    TableSchemaMeta t = tableWithColumn("id");
    String v1 = SchemaMetaVersionUtil.computeVersion(List.of(t));
    String v2 = SchemaMetaVersionUtil.computeVersion(List.of(t));
    assertThat(v1).isEqualTo(v2);
  }

  private static TableSchemaMeta tableWithColumn(String... columns) {
    TableSchemaMeta meta = new TableSchemaMeta();
    meta.setTableName("users");
    meta.setEstimatedRows(100L);
    for (String col : columns) {
      ColumnMeta column = new ColumnMeta();
      column.setColumnName(col);
      column.setDataType("varchar");
      meta.getColumns().add(column);
    }
    return meta;
  }
}
