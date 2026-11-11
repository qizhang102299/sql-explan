import type { DbRoutineTypeEnum, Long } from './models'

export interface DataSourceSaveReq {
  id?: Long
  name: string
  host: string
  port: number
  dbName: string
  username: string
  password?: string
}

export interface DataSourceDeleteReq {
  id: Long
}

export interface DataSourceDetailReq {
  id: Long
}

export interface DataSourcePageReq {
  pageNum?: number
  pageSize?: number
  nameKeyword?: string
}

export interface DataSourceTestReq {
  dataSourceId?: Long
  host?: string
  port?: number
  dbName?: string
  username?: string
  password?: string
}

export interface SqlAnalyzeReq {
  dataSourceId: Long
  sql: string
  enableBenchmark?: boolean
  benchmarkIterations?: number
  verifyIndexBenefit?: boolean
}

export interface SqlValidateReq {
  sql: string
  dataSourceId?: Long
}

export interface SqlHistoryPageReq {
  pageNum?: number
  pageSize?: number
  dataSourceId?: Long
}

export interface SqlHistoryDetailReq {
  id: Long
}

export interface DbObjectListReq {
  dataSourceId: Long
}

export interface DbObjectTableDetailReq {
  dataSourceId: Long
  tableName: string
}

export interface DbObjectViewDetailReq {
  dataSourceId: Long
  viewName: string
}

export interface DbObjectRoutineDetailReq {
  dataSourceId: Long
  routineName: string
  routineType: DbRoutineTypeEnum
}
