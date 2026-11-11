package com.sqlexplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sqlexplan.constant.TraceIdConstants;
import com.sqlexplan.mapper.DataSourceConfigMapper;
import com.sqlexplan.mapper.SqlAnalyzeRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 应用上下文与 HTTP 启动冒烟测试（不依赖本机 MySQL，完整库验证见 docker compose 联调）。
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
class SqlExplanApplicationTests {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private DataSourceConfigMapper dataSourceConfigMapper;

  @MockBean
  private SqlAnalyzeRecordMapper sqlAnalyzeRecordMapper;

  @Test
  void contextLoads() {
    assertThat(true).isTrue();
  }

  @Test
  void healthEndpointReturnsResultEnvelope() throws Exception {
    MvcResult mvcResult = mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("UP"))
        .andExpect(jsonPath("$.timestamp").isNumber())
        .andReturn();

    String traceId = mvcResult.getResponse().getHeader(TraceIdConstants.TRACE_ID_HEADER);
    assertThat(traceId).isNotBlank();
  }
}
