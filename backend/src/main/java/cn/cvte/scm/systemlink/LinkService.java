package cn.cvte.scm.systemlink;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.config.ConfigService;
import cn.cvte.scm.todo.service.TodoReadService;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LinkService {
  private final JdbcTemplate db;
  private final TodoReadService todos;
  private final AuditStore audit;

  public LinkService(JdbcTemplate d, TodoReadService t, AuditStore a) {
    db = d;
    todos = t;
    audit = a;
  }

  public List<Map<String, Object>> list() {
    return db.queryForList("SELECT * FROM system_link_template ORDER BY id");
  }

  @org.springframework.transaction.annotation.Transactional
  public void save(long sourceId, String page, String template) {
    if (page == null
        || !page.matches("[A-Za-z0-9_-]{1,100}")
        || template == null
        || template.length() > 1000) throw BusinessException.bad("页面代码或链接模板无效");
    String probe = template;
    for (String key : List.of("businessNo", "pageCode", "taskId", "sourceSystem"))
      probe = probe.replace("${" + key + "}", "sample");
    if (probe.contains("${")) throw BusinessException.bad("模板包含未知变量");
    ConfigService.validLink(probe);
    if (probe.startsWith("/")) throw BusinessException.bad("深链接须为完整 HTTP(S) URL");
    if (db.queryForObject("SELECT COUNT(*) FROM data_source WHERE id=?", Integer.class, sourceId)
        == 0) throw BusinessException.missing();
    db.update(
        "INSERT INTO system_link_template(source_id,page_code,url_template) VALUES(?,?,?) ON"
            + " DUPLICATE KEY UPDATE url_template=VALUES(url_template)",
        sourceId,
        page,
        template);
    audit.record("LINK_CONFIG", page, "SUCCESS");
  }

  public Map<String, String> resolve(long taskId, String page) {
    var t = todos.get(taskId);
    String code = page == null || page.isBlank() ? t.sourcePage : page;
    var rows =
        db.queryForList(
            "SELECT url_template FROM system_link_template WHERE source_id=? AND page_code=?",
            String.class,
            t.sourceId,
            code);
    if (rows.isEmpty()) throw BusinessException.bad("该来源页面尚未配置链接模板");
    String url = rows.get(0);
    Map<String, String> vars =
        Map.of(
            "businessNo",
            t.businessNo,
            "pageCode",
            code,
            "taskId",
            t.id.toString(),
            "sourceSystem",
            t.sourceSystem);
    for (var e : vars.entrySet())
      url =
          url.replace(
              "${" + e.getKey() + "}",
              URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
    ConfigService.validLink(url);
    audit.record("SYSTEM_JUMP", t.id + ":" + code, "RESOLVED");
    return Map.of("url", url, "pageCode", code);
  }
}
