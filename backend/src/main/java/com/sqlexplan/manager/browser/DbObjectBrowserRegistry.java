package com.sqlexplan.manager.browser;

import com.sqlexplan.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按数据库类型解析对象浏览器实现（首期仅 MySQL）。
 */
@Component
@RequiredArgsConstructor
public class DbObjectBrowserRegistry {

  private final MysqlDbObjectBrowser mysqlDbObjectBrowser;

  /**
   * 获取当前支持的对象浏览器。
   *
   * @return 浏览器实现
   */
  public DbObjectBrowser getBrowser() {
    if (mysqlDbObjectBrowser == null) {
      throw new BizException("未配置数据库对象浏览器");
    }
    return mysqlDbObjectBrowser;
  }
}
