package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.ExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出任务数据访问。
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {
}
