package cn.cvte.scm.integration.store;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.model.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceStore {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public SourceStore(JdbcTemplate d, ObjectMapper j) {
    db = d;
    json = j;
  }

  public List<Source> list() {
    return db.query(
        "SELECT * FROM data_source ORDER BY id",
        (r, i) -> {
          Source s = new Source();
          s.id = r.getLong("id");
          s.name = r.getString("name");
          s.type = r.getString("type");
          s.endpoint = r.getString("endpoint");
          s.requestMethod = r.getString("request_method");
          s.timeoutMs = r.getInt("timeout_ms");
          s.refreshInterval = r.getInt("refresh_interval");
          s.enabled = r.getBoolean("enabled");
          s.syncStatus = r.getString("sync_status");
          s.lastError = r.getString("last_error");
          var at = r.getTimestamp("last_sync_at");
          s.lastSyncAt = at == null ? null : at.toLocalDateTime();
          try {
            s.mappingConfig =
                json.readTree(
                    r.getString("mapping_config") == null ? "{}" : r.getString("mapping_config"));
          } catch (Exception e) {
            throw new IllegalStateException();
          }
          return s;
        });
  }

  public java.util.Map<Long, java.time.LocalDateTime> dataUpdates() {
    return db.query(
        "SELECT source_id, MAX(source_updated_at) AS data_updated_at FROM scm_todo"
            + " WHERE source_updated_at IS NOT NULL GROUP BY source_id",
        rows -> {
          var updates = new java.util.HashMap<Long, java.time.LocalDateTime>();
          while (rows.next()) {
            updates.put(
                rows.getLong("source_id"),
                rows.getObject("data_updated_at", java.time.LocalDateTime.class));
          }
          return updates;
        });
  }

  public Source get(long id) {
    return list().stream()
        .filter(s -> s.id == id)
        .findFirst()
        .orElseThrow(BusinessException::missing);
  }

  public Source save(Source s) {
    if (s.id == null) {
      db.update(
          "INSERT INTO"
              + " data_source(name,type,endpoint,request_method,timeout_ms,refresh_interval,mapping_config,enabled)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          s.name,
          s.type,
          s.endpoint,
          s.requestMethod,
          s.timeoutMs,
          s.refreshInterval,
          s.mappingConfig.toString(),
          s.enabled);
      return list().stream().filter(x -> x.name.equals(s.name)).findFirst().orElseThrow();
    }
    get(s.id);
    db.update(
        "UPDATE data_source SET"
            + " name=?,type=?,endpoint=?,request_method=?,timeout_ms=?,refresh_interval=?,mapping_config=?,enabled=?"
            + " WHERE id=?",
        s.name,
        s.type,
        s.endpoint,
        s.requestMethod,
        s.timeoutMs,
        s.refreshInterval,
        s.mappingConfig.toString(),
        s.enabled,
        s.id);
    return get(s.id);
  }

  public void status(long id, String status, String error) {
    db.update(
        "UPDATE data_source SET sync_status=?,last_error=?,last_sync_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        status,
        error,
        id);
  }
}
