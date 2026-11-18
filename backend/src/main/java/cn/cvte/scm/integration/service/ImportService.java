package cn.cvte.scm.integration.service;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.adapter.InputRow;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.todo.mapper.TodoMapper;
import cn.cvte.scm.todo.model.Todo;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {
  private final JdbcTemplate db;
  private final TodoMapper todos;
  private final TodoConverter converter;
  private final AuditStore audit;

  public ImportService(JdbcTemplate db, TodoMapper t, TodoConverter c, AuditStore a) {
    this.db = db;
    todos = t;
    converter = c;
    audit = a;
  }

  @Transactional
  public Map<String, Object> ingest(Source source, String filename, List<InputRow> rows) {
    // Serialize batches for each source; database errors roll back this entire batch, validation
    // errors do not.
    db.queryForObject("SELECT id FROM data_source WHERE id=? FOR UPDATE", Long.class, source.id);
    var keys = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO import_batch(source_id,file_name) VALUES(?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          p.setLong(1, source.id);
          p.setString(2, filename);
          return p;
        },
        keys);
    long id = keys.getKey().longValue();
    int success = 0, failed = 0, duplicate = 0, updated = 0;
    for (InputRow row : rows) {
      Todo t;
      try {
        t = converter.convert(source, row);
      } catch (BusinessException e) {
        failed++;
        db.update(
            "INSERT INTO import_error(batch_id,`row_number`,reason) VALUES(?,?,?)",
            id,
            row.line(),
            e.getMessage());
        continue;
      }
      Todo existing = todos.existing(source.id, t.sourceRecordId);
      if (existing == null) {
        todos.insert(t);
        success++;
      } else if (!t.sourceUpdatedAt.isAfter(existing.sourceUpdatedAt)) {
        duplicate++;
      } else {
        t.id = existing.id;
        todos.update(t);
        updated++;
        success++;
      }
    }
    String status = failed > 0 ? "PARTIAL" : "SUCCESS";
    db.update(
        "UPDATE import_batch SET"
            + " success_count=?,failure_count=?,duplicate_count=?,updated_count=?,status=? WHERE"
            + " id=?",
        success,
        failed,
        duplicate,
        updated,
        status,
        id);
    db.update(
        "UPDATE data_source SET sync_status=?,last_error=?,last_sync_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        status,
        failed > 0 ? "存在错误行，请查看导入批次" : null,
        source.id);
    audit.record(
        filename.startsWith("HTTP:") ? "HTTP_SYNC" : "FILE_IMPORT", Long.toString(id), status);
    return batch(id);
  }

  public Map<String, Object> batch(long id) {
    var list = db.queryForList("SELECT * FROM import_batch WHERE id=?", id);
    if (list.isEmpty()) throw BusinessException.missing();
    Map<String, Object> result = new LinkedHashMap<>(list.get(0));
    result.put(
        "errors",
        db.queryForList(
            "SELECT `row_number` AS line,reason FROM import_error WHERE batch_id=? ORDER BY id",
            id));
    return result;
  }

  public List<Map<String, Object>> batches() {
    return db.queryForList("SELECT * FROM import_batch ORDER BY id DESC LIMIT 100");
  }
}
