package com.sqlexplan.constant;

/**
 * 链路追踪相关常量。
 */
public final class TraceIdConstants {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  public static final String MDC_TRACE_ID_KEY = "traceId";

  private TraceIdConstants() {
  }
}
