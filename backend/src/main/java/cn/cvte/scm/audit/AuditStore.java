package cn.cvte.scm.audit;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditStore {
  private final JdbcTemplate db;

  public AuditStore(JdbcTemplate db) {
    this.db = db;
  }

  public void record(String type, String target, String result) {
    db.update(
        "INSERT INTO operation_log(operation_type,target,result) VALUES(?,?,?)",
        type,
        target,
        result);
  }

  public List<Map<String, Object>> list() {
    return db.queryForList("SELECT * FROM operation_log ORDER BY id DESC LIMIT 200");
  }
}
