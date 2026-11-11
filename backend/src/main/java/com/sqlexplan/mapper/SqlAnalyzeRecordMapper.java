package com.sqlexplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sqlexplan.model.entity.SqlAnalyzeRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 分析历史 Mapper。
 */
@Mapper
public interface SqlAnalyzeRecordMapper extends BaseMapper<SqlAnalyzeRecordEntity> {
}
