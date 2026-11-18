package cn.cvte.scm.integration.service;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.adapter.ConfiguredHttpAdapter;
import cn.cvte.scm.integration.store.SourceStore;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SyncService {
  private final SourceStore sources;
  private final ConfiguredHttpAdapter http;
  private final ImportService imports;
  private final AuditStore audit;

  public SyncService(SourceStore s, ConfiguredHttpAdapter h, ImportService i, AuditStore a) {
    sources = s;
    http = h;
    imports = i;
    audit = a;
  }

  public synchronized Map<String, Object> sync(long id) {
    var source = sources.get(id);
    if (!"HTTP".equals(source.type)) throw BusinessException.bad("文件数据源请使用文件导入");
    try {
      return imports.ingest(source, "HTTP:" + id, http.fetch(source));
    } catch (Exception e) {
      sources.status(id, "FAILED", "同步失败，检查源服务、配置或数据库；未覆盖已存数据");
      audit.record("HTTP_SYNC", Long.toString(id), "FAILED");
      throw e;
    }
  }
}
