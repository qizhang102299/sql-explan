package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.IndexColumnMeta;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link IndexAdvisor} 补充单元测试。
 */
class IndexAdvisorTest {

  private SqlParseService sqlParseService;

  private IndexAdvisor indexAdvisor;

  @BeforeEach
  void setUp() {
    sqlParseService = new SqlParseService();
    indexAdvisor = new IndexAdvisor();
  }

  @Test
  void columnOrderEqualityBeforeRange() {
    String sql =
        "SELECT id FROM t WHERE a = 1 AND b > 10 ORDER BY c";
    SqlStructure structure = sqlParseService.parse(sql);
    TableSchemaMeta meta = tableWithIndexes("t", List.of());
    IndexAdviceReport report = indexAdvisor.advise(structure, List.of(meta), null);
    assertThat(report.getAdvices()).isNotEmpty();
    String ddl = report.getAdvices().get(0).getCreateIndexDdl();
    assertThat(ddl).matches("(?i).*\\(a, b, c\\).*");
  }

  @Test
  void compositeIndexWithoutFullProjectionIsNotCovering() {
    String sql =
        "SELECT id FROM users WHERE a = 1 AND b > 10 ORDER BY c";
    SqlStructure structure = sqlParseService.parse(sql);
    TableSchemaMeta meta = tableWithIndexes("users", List.of());
    IndexAdviceReport report = indexAdvisor.advise(structure, List.of(meta), null);
    assertThat(report.getAdvices()).isNotEmpty();
    assertThat(report.getAdvices().get(0).getAdviceType())
        .isEqualTo(IndexAdviceTypeEnum.CREATE_INDEX);
    assertThat(report.getAdvices().get(0).getCreateIndexDdl()).doesNotContain("/*");
  }

  @Test
  void marksCoveringWhenIndexIncludesAllSelectColumns() {
    String sql = "SELECT id, status FROM users WHERE status = 2";
    SqlStructure structure = sqlParseService.parse(sql);
    TableSchemaMeta meta = tableWithIndexes("users", List.of());
    IndexAdviceReport report = indexAdvisor.advise(structure, List.of(meta), null);
    assertThat(report.getAdvices())
        .anyMatch(
            a ->
                a.getAdviceType() == IndexAdviceTypeEnum.COVERING_INDEX
                    && a.getCreateIndexDdl() != null
                    && !a.getCreateIndexDdl().contains("/*")
                    && a.getReason() != null
                    && a.getReason().contains("覆盖索引"));
  }

  @Test
  void detectsReuseExistingLeftPrefix() {
    String sql = "SELECT id FROM users WHERE status = 1";
    SqlStructure structure = sqlParseService.parse(sql);
    IndexMeta idx = new IndexMeta();
    idx.setIndexName("idx_status_name");
    IndexColumnMeta c1 = new IndexColumnMeta();
    c1.setColumnName("status");
    c1.setSeqInIndex(1);
    IndexColumnMeta c2 = new IndexColumnMeta();
    c2.setColumnName("name");
    c2.setSeqInIndex(2);
    idx.setColumns(List.of(c1, c2));
    TableSchemaMeta meta = tableWithIndexes("users", List.of(idx));
    IndexAdviceReport report = indexAdvisor.advise(structure, List.of(meta), null);
    assertThat(report.getAdvices())
        .anyMatch(
            a -> a.getAdviceType() == IndexAdviceTypeEnum.REUSE_EXISTING_INDEX
                && "idx_status_name".equals(a.getRelatedIndexName()));
  }

  private static TableSchemaMeta tableWithIndexes(String table, List<IndexMeta> indexes) {
    TableSchemaMeta meta = new TableSchemaMeta();
    meta.setTableName(table);
    meta.setEstimatedRows(1000L);
    meta.setIndexes(indexes);
    return meta;
  }
}
