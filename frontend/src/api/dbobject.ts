import { postJson } from './request'
import type {
  DbObjectListRes,
  DbRoutineDetailRes,
  DbTableDetailRes,
  DbViewDetailRes,
} from '@/types/models'
import type {
  DbObjectListReq,
  DbObjectRoutineDetailReq,
  DbObjectTableDetailReq,
  DbObjectViewDetailReq,
} from '@/types/req'

const PREFIX = '/datasource/object'

export function dbObjectList(req: DbObjectListReq): Promise<DbObjectListRes> {
  return postJson(`${PREFIX}/list`, req)
}

export function dbObjectTableDetail(req: DbObjectTableDetailReq): Promise<DbTableDetailRes> {
  return postJson(`${PREFIX}/table-detail`, req)
}

export function dbObjectViewDetail(req: DbObjectViewDetailReq): Promise<DbViewDetailRes> {
  return postJson(`${PREFIX}/view-detail`, req)
}

export function dbObjectRoutineDetail(
  req: DbObjectRoutineDetailReq,
): Promise<DbRoutineDetailRes> {
  return postJson(`${PREFIX}/routine-detail`, req)
}
