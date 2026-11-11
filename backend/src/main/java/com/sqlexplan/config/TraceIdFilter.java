package com.sqlexplan.config;

import com.sqlexplan.constant.TraceIdConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个 HTTP 请求注入 traceId，便于日志关联。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String traceId = request.getHeader(TraceIdConstants.TRACE_ID_HEADER);
    if (!StringUtils.hasText(traceId)) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }
    MDC.put(TraceIdConstants.MDC_TRACE_ID_KEY, traceId);
    response.setHeader(TraceIdConstants.TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TraceIdConstants.MDC_TRACE_ID_KEY);
    }
  }
}
