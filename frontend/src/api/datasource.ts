import { postJson } from './request'
import type {
  DataSourcePageRes,
  DataSourceRes,
  DataSourceTestRes,
} from '@/types/models'
import type {
  DataSourceDeleteReq,
  DataSourceDetailReq,
  DataSourcePageReq,
  DataSourceSaveReq,
  DataSourceTestReq,
} from '@/types/req'

const PREFIX = '/datasource'

export function saveDataSource(req: DataSourceSaveReq): Promise<DataSourceRes> {
  return postJson(`${PREFIX}/save`, req)
}

export function deleteDataSource(req: DataSourceDeleteReq): Promise<void> {
  return postJson(`${PREFIX}/delete`, req)
}

export function dataSourceDetail(req: DataSourceDetailReq): Promise<DataSourceRes> {
  return postJson(`${PREFIX}/detail`, req)
}

export function dataSourcePage(req: DataSourcePageReq): Promise<DataSourcePageRes> {
  return postJson(`${PREFIX}/page`, req)
}

export function testDataSource(req: DataSourceTestReq): Promise<DataSourceTestRes> {
  return postJson(`${PREFIX}/test`, req)
}
