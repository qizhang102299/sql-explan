import type { SqlValidateRes } from '@/types/models'

/**
 * 业务失败但携带 SQL 校验详情。
 */
export class ApiBizError extends Error {
  readonly validation?: SqlValidateRes

  constructor(message: string, validation?: SqlValidateRes) {
    super(message)
    this.name = 'ApiBizError'
    this.validation = validation
  }
}

export function isSqlValidateRes(data: unknown): data is SqlValidateRes {
  if (data == null || typeof data !== 'object') {
    return false
  }
  return 'valid' in data || 'errorType' in data
}
