import { postJson } from './request'
import type { SqlAnalyzeRes, SqlHistoryPageRes, SqlValidateRes } from '@/types/models'
import type {
  SqlAnalyzeReq,
  SqlHistoryDetailReq,
  SqlHistoryPageReq,
  SqlValidateReq,
} from '@/types/req'

const PREFIX = '/sql'

export function analyzeSql(req: SqlAnalyzeReq): Promise<SqlAnalyzeRes> {
  return postJson(`${PREFIX}/analyze`, req)
}

export function historyPage(req: SqlHistoryPageReq): Promise<SqlHistoryPageRes> {
  return postJson(`${PREFIX}/history/page`, req)
}

export function historyDetail(req: SqlHistoryDetailReq): Promise<SqlAnalyzeRes> {
  return postJson(`${PREFIX}/history/detail`, req)
}

export function validateSql(req: SqlValidateReq): Promise<SqlValidateRes> {
  return postJson(`${PREFIX}/validate`, req)
}
