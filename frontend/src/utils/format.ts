import type {
  AnalysisSourceEnum,
  IndexAdviceTypeEnum,
  PlanIssueSeverityEnum,
  PlanIssueTypeEnum,
  PlanNodeKindEnum,
} from '@/types/models'

export function formatTimestamp(ms?: number | null): string {
  if (ms == null || ms <= 0) {
    return '-'
  }
  const d = new Date(ms)
  if (Number.isNaN(d.getTime())) {
    return String(ms)
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function formatPercent(ratio?: number | null): string {
  if (ratio == null || Number.isNaN(ratio)) {
    return '-'
  }
  return `${(ratio * 100).toFixed(1)}%`
}

export function analysisSourceLabel(src?: AnalysisSourceEnum): string {
  if (src === 'AI') {
    return 'AI'
  }
  if (src === 'LOCAL_RULES') {
    return '本地规则'
  }
  return '-'
}

const severityMap: Record<PlanIssueSeverityEnum, string> = {
  CRITICAL: '严重',
  WARNING: '警告',
  INFO: '提示',
}

export function severityLabel(s?: PlanIssueSeverityEnum): string {
  return s ? severityMap[s] ?? s : '-'
}

export function severityTagType(
  s?: PlanIssueSeverityEnum,
): 'danger' | 'warning' | 'info' {
  if (s === 'CRITICAL') {
    return 'danger'
  }
  if (s === 'WARNING') {
    return 'warning'
  }
  return 'info'
}

const issueTypeMap: Partial<Record<PlanIssueTypeEnum, string>> = {
  FULL_TABLE_SCAN: '全表扫描',
  INDEX_NOT_USED: '索引未使用',
  FILESORT: '文件排序',
  TEMPORARY_TABLE: '临时表',
  LARGE_ROWS_EXAMINED: '扫描行数过大',
  IMPLICIT_TYPE_CONVERSION: '隐式类型转换',
  INDEX_COLUMN_FUNCTION: '索引列被函数包裹',
  DEEP_PAGINATION: '深分页',
  LIKE_LEADING_WILDCARD: 'LIKE 前导通配',
  NON_SARGABLE_OPERATOR: '不利于索引的运算符',
  LIMIT_WITHOUT_ORDER_BY: 'LIMIT 无 ORDER BY',
  COUNT_STAR_LARGE_TABLE: '大表 COUNT(*)',
  JOIN_COLLATION_MISMATCH: 'JOIN 排序规则不一致',
  CORRELATED_SUBQUERY: '关联子查询',
  INDEX_COLUMN_EXPRESSION: '索引列表达式',
}

export function issueTypeLabel(t?: PlanIssueTypeEnum): string {
  return t ? issueTypeMap[t] ?? t : '-'
}

const nodeKindMap: Record<PlanNodeKindEnum, string> = {
  TABLE: '表访问',
  NESTED_LOOP: '嵌套循环',
  ORDERING: '排序',
  GROUPING: '分组',
  DUPLICATES_REMOVAL: '去重',
  MATERIALIZED: '物化',
  OPERATION: '运算',
}

export function nodeKindLabel(k?: PlanNodeKindEnum): string {
  return k ? nodeKindMap[k] ?? k : '节点'
}

const adviceTypeMap: Partial<Record<IndexAdviceTypeEnum, string>> = {
  CREATE_INDEX: '新建索引',
  REUSE_EXISTING_INDEX: '复用已有索引',
  COVERING_INDEX: '覆盖索引',
  REDUNDANT_INDEX: '冗余索引',
  DUPLICATE_INDEX: '重复索引',
  NON_SARGABLE_OPERATOR_HINT: '谓词提示',
}

export function adviceTypeLabel(t?: IndexAdviceTypeEnum): string {
  return t ? adviceTypeMap[t] ?? t : '-'
}

export function truncateSql(sql: string, maxLen = 120): string {
  const s = sql.replace(/\s+/g, ' ').trim()
  if (s.length <= maxLen) {
    return s
  }
  return `${s.slice(0, maxLen)}…`
}
