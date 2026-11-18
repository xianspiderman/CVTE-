package cn.cvte.scm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AiAudit {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  AiAudit(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  void record(String tool, JsonNode arguments, String state, long started, String error) {
    // Keep the argument shape and outcome. Free text, business records and credentials are
    // excluded.
    var fields = json.createArrayNode();
    if (arguments != null) arguments.fieldNames().forEachRemaining(fields::add);
    db.update(
        "INSERT INTO ai_tool_log(tool_name,arguments,result,duration_ms,error_message)"
            + " VALUES(?,?,?,?,?)",
        tool,
        json.createObjectNode().set("fields", fields).toString(),
        json.createObjectNode().put("status", state).toString(),
        (System.nanoTime() - started) / 1_000_000,
        error);
  }

  List<Map<String, Object>> list() {
    return db.queryForList(
        "SELECT id,tool_name,JSON_UNQUOTE(JSON_EXTRACT(result,'$.status')) AS status,"
            + " duration_ms,error_message,created_at FROM ai_tool_log ORDER BY id DESC LIMIT 200");
  }
}
