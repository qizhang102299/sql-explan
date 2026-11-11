/** 与后端枚举、领域模型字段严格对齐 */

export type AnalysisSourceEnum = 'LOCAL_RULES' | 'AI'

export type PlanNodeKindEnum =
  | 'TABLE'
  | 'NESTED_LOOP'
  | 'ORDERING'
  | 'GROUPING'
  | 'DUPLICATES_REMOVAL'
  | 'MATERIALIZED'
  | 'OPERATION'

export type PlanIssueSeverityEnum = 'CRITICAL' | 'WARNING' | 'INFO'

export type PlanIssueTypeEnum =
  | 'FULL_TABLE_SCAN'
  | 'INDEX_NOT_USED'
  | 'FILESORT'
  | 'TEMPORARY_TABLE'
  | 'LARGE_ROWS_EXAMINED'
  | 'IMPLICIT_TYPE_CONVERSION'
  | 'INDEX_COLUMN_FUNCTION'
  | 'DEEP_PAGINATION'
  | 'LIKE_LEADING_WILDCARD'
  | 'NON_SARGABLE_OPERATOR'
  | 'LIMIT_WITHOUT_ORDER_BY'
  | 'COUNT_STAR_LARGE_TABLE'
  | 'JOIN_COLLATION_MISMATCH'
  | 'CORRELATED_SUBQUERY'
  | 'INDEX_COLUMN_EXPRESSION'

export type IndexAdviceTypeEnum =
  | 'CREATE_INDEX'
  | 'REUSE_EXISTING_INDEX'
  | 'COVERING_INDEX'
  | 'REDUNDANT_INDEX'
  | 'DUPLICATE_INDEX'
  | 'NON_SARGABLE_OPERATOR_HINT'

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: Long
}

export type Long = number

export interface DataSourceRes {
  id: Long
  name: string
  host: string
  port: number
  dbName: string
  username: string
  createdTime: Long
  updatedTime: Long
}

export interface DataSourcePageRes {
  total: Long
  records: DataSourceRes[]
}

export interface DataSourceTestRes {
  success: boolean
  message: string
  elapsedMs: Long
}

export interface SqlSemanticInsight {
  intentSummary?: string
  involvedTables?: string[]
  filtersDescription?: string
  joinsDescription?: string
  aggregationDescription?: string
  orderingDescription?: string
  paginationDescription?: string
  subqueryCteDescription?: string
  riskNotes?: string[]
}

export interface PlanNode {
  nodeKind?: PlanNodeKindEnum
  operationLabel?: string
  tableName?: string
  accessType?: string
  possibleKeys?: string[]
  indexKey?: string
  rowsExaminedPerScan?: Long
  rowsProducedPerJoin?: Long
  filteredPercent?: number
  extras?: string[]
  attachedCondition?: string
  children?: PlanNode[]
}

export interface ExplainPlan {
  root?: PlanNode
  parseWarnings?: string[]
}

export interface PlanIssue {
  issueType?: PlanIssueTypeEnum
  severity?: PlanIssueSeverityEnum
  title?: string
  description?: string
  relatedTable?: string
  evidence?: string
  remediationSql?: string
}

export interface PlanInterpretation {
  issues?: PlanIssue[]
  summary?: string
}

export interface SqlPlanRes {
  explainPlan?: ExplainPlan
  interpretation?: PlanInterpretation
  narrative?: string
}

export interface IndexAdvice {
  adviceType?: IndexAdviceTypeEnum
  severity?: PlanIssueSeverityEnum
  tableName?: string
  createIndexDdl?: string
  reason?: string
  expectedBenefit?: string
  writeCostNote?: string
  relatedIndexName?: string
}

export interface IndexAdviceReport {
  advices?: IndexAdvice[]
  summary?: string
}

export interface ResultEquivalenceReport {
  equivalent: boolean
  originalRowCount: number
  optimizedRowCount: number
  originalChecksum?: string
  optimizedChecksum?: string
  checksumSkipped: boolean
  message?: string
}

export interface ValidatedRewriteCandidate {
  sql?: string
  explanation?: string
}

export interface OptimizedSqlRes {
  sql?: string
  explanation?: string
  equivalencePassed: boolean
  equivalenceReport?: ResultEquivalenceReport
  passedCandidates?: ValidatedRewriteCandidate[]
}

export interface BenchmarkLatencyStats {
  executionCount: number
  medianMs?: Long
  p95Ms?: Long
  minMs?: Long
  maxMs?: Long
}

export interface BenchmarkReport {
  executed: boolean
  iterations?: number
  originalStats?: BenchmarkLatencyStats
  optimizedStats?: BenchmarkLatencyStats
  improvementRatio?: number
  equivalence?: ResultEquivalenceReport
  equivalenceWarning: boolean
  warningMessage?: string
  indexBenefitVerified: boolean
  originalMedianBeforeIndexMs?: Long
  originalMedianAfterIndexMs?: Long
  indexVerificationNote?: string
}

export interface SqlAnalyzeRes {
  recordId?: Long
  analysisSource?: AnalysisSourceEnum
  aiStatusNote?: string
  semantic?: SqlSemanticInsight
  plan?: SqlPlanRes
  indexAdvices?: IndexAdviceReport
  optimizedSql?: OptimizedSqlRes
  benchmark?: BenchmarkReport
  createdTime?: Long
}

export interface SqlHistoryRecordSummaryRes {
  id: Long
  dataSourceId: Long
  sqlText: string
  sqlFingerprint?: string
  costMs?: Long
  createdTime: Long
}

export interface SqlHistoryPageRes {
  total: Long
  records: SqlHistoryRecordSummaryRes[]
}
