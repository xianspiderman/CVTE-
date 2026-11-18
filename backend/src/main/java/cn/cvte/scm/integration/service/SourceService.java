package cn.cvte.scm.integration.service;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.store.SourceStore;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SourceService {
  private final SourceStore store;
  private final AuditStore audit;
  private final int port;

  public SourceService(SourceStore s, AuditStore a, @Value("${scm.mock-port:4010}") int p) {
    store = s;
    audit = a;
    port = p;
  }

  public List<Source> list() {
    return store.list();
  }

  public Map<Long, java.time.LocalDateTime> dataUpdates() {
    return store.dataUpdates();
  }

  public Source get(long id) {
    return store.get(id);
  }

  public void validEndpoint(String url) {
    try {
      URI u = URI.create(url);
      if (!"http".equals(u.getScheme())
          || !"127.0.0.1".equals(u.getHost())
          || u.getPort() != port
          || u.getUserInfo() != null
          || u.getFragment() != null
          || !u.getPath().startsWith("/api/")) throw new Exception();
    } catch (Exception e) {
      throw BusinessException.bad("本轮数据源仅允许项目 Mock 的 http://127.0.0.1:" + port + "/api/ 地址");
    }
  }

  @org.springframework.transaction.annotation.Transactional
  public Source save(Source s) {
    if (s.mappingConfig == null || !s.mappingConfig.isObject())
      throw BusinessException.bad("字段映射须为目标字段到源字段的 JSON 对象");
    Set<String> fields =
        Set.of(
            "sourceRecordId",
            "businessNo",
            "title",
            "description",
            "businessType",
            "sourcePage",
            "status",
            "riskLevel",
            "priority",
            "owner",
            "businessOrg",
            "businessUrl",
            "extraJson",
            "createdAt",
            "dueAt",
            "sourceUpdatedAt",
            "completedAt");
    s.mappingConfig
        .fields()
        .forEachRemaining(
            e -> {
              if (!fields.contains(e.getKey())
                  || !e.getValue().isTextual()
                  || e.getValue().asText().isBlank()) throw BusinessException.bad("映射值必须为非空字段名");
            });
    if ("HTTP".equals(s.type)) validEndpoint(s.endpoint);
    if (s.id != null && !get(s.id).type.equals(s.type)) throw BusinessException.bad("已有数据源类型不可更改");
    try {
      Source saved = store.save(s);
      audit.record("SOURCE_CONFIG", saved.id.toString(), "SUCCESS");
      return saved;
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw BusinessException.bad("数据源名称重复");
    }
  }
}
