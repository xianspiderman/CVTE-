package cn.cvte.scm.config;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuditStore audit;

  public ConfigService(JdbcTemplate db, ObjectMapper json, AuditStore audit) {
    this.db = db;
    this.json = json;
    this.audit = audit;
  }

  public JsonNode get() {
    var rows =
        db.queryForList(
            "SELECT config_json FROM workbench_config WHERE user_key='demo'", String.class);
    return decode(rows);
  }

  private JsonNode decode(List<String> rows) {
    try {
      return rows.isEmpty() ? defaults() : json.readTree(rows.get(0));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored config");
    }
  }

  public int threshold() {
    return get().path("dueSoonHours").asInt(24);
  }

  public JsonNode defaults() {
    ObjectNode c = json.createObjectNode();
    c.put("dueSoonHours", 24);
    c.put("defaultDays", 30);
    c.put("aiExpanded", true);
    c.put("defaultView", "LIST");
    c.put("defaultOwner", "");
    String[] names = {"我的工作台", "采购待办", "库存异常", "供应商协同", "数据分析", "工作台配置", "数据源配置", "操作日志"};
    String[] paths = {
      "/",
      "/procurement",
      "/inventory",
      "/suppliers",
      "/analytics",
      "/settings",
      "/data-sources",
      "/logs"
    };
    ArrayNode nav = c.putArray("navigation");
    for (int i = 0; i < names.length; i++) {
      var n = nav.addObject();
      n.put("key", paths[i]);
      n.put("name", names[i]);
      n.put("path", paths[i]);
      n.put("visible", true);
      n.put("order", i);
      n.put("icon", "app");
      n.put("parent", "");
    }
    String[] keys = {
      "TOTAL",
      "TODAY_NEW",
      "OVERDUE",
      "DUE_SOON",
      "HIGH_RISK",
      "PENDING_REVIEW",
      "PENDING_REPLY",
      "WEEK_COMPLETED",
      "AVG_HOURS",
      "ON_TIME_RATE"
    };
    String[] labels = {
      "待办总数", "今日新增", "已逾期", "即将逾期", "高风险", "待审核", "待回复", "本周完成", "平均处理时长", "按时完成率"
    };
    ArrayNode cards = c.putArray("cards");
    for (int i = 0; i < keys.length; i++) {
      var n = cards.addObject();
      n.put("key", keys[i]);
      n.put("metric", keys[i]);
      n.put("name", labels[i]);
      n.put("visible", true);
      n.put("order", i);
      n.put("color", "#305ee8");
      n.put("icon", "chart");
      n.put("days", 30);
      n.put("refreshSeconds", 60);
      n.put("clickBehavior", "DETAIL");
    }
    ArrayNode links = c.putArray("shortcuts");
    for (int i = 1; i < 4; i++) {
      var n = links.addObject();
      n.put("key", "quick" + i);
      n.put("name", names[i]);
      n.put("path", paths[i]);
      n.put("visible", true);
      n.put("order", i);
      n.put("icon", "link");
      n.put("aiPrompt", "");
    }
    return c;
  }

  public static void validLink(String url) {
    try {
      if (url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")) {
        if (!url.matches("/[A-Za-z0-9/_?=&%.-]*")) throw new Exception();
        return;
      }
      URI u = URI.create(url);
      if (!Set.of("http", "https").contains(u.getScheme())
          || u.getHost() == null
          || u.getUserInfo() != null) throw new Exception();
    } catch (Exception e) {
      throw BusinessException.bad("仅允许有效的内部路由或 HTTP(S) 链接");
    }
  }

  @Transactional
  public JsonNode saveIfUnchanged(JsonNode expected, JsonNode replacement) {
    db.update(
        "INSERT INTO workbench_config(user_key,config_json) VALUES('demo',?) ON DUPLICATE KEY"
            + " UPDATE user_key=user_key",
        defaults().toString());
    var locked =
        db.queryForList(
            "SELECT config_json FROM workbench_config WHERE user_key='demo' FOR UPDATE",
            String.class);
    if (!decode(locked).equals(expected))
      throw new BusinessException(
          org.springframework.http.HttpStatus.CONFLICT, "工作台配置已变化，请重新生成修改提案");
    return save(replacement);
  }

  @Transactional
  public JsonNode save(JsonNode c) {
    if (c == null
        || !c.isObject()
        || !c.path("dueSoonHours").isInt()
        || c.path("dueSoonHours").asInt() < 1
        || c.path("dueSoonHours").asInt() > 168
        || !c.path("defaultDays").isInt()
        || !c.path("aiExpanded").isBoolean()
        || !c.path("defaultOwner").isTextual()
        || c.path("defaultOwner").asText().length() > 100
        || c.path("defaultDays").asInt() < 1
        || c.path("defaultDays").asInt() > 365
        || !Set.of("LIST", "ANALYTICS").contains(c.path("defaultView").asText()))
      throw BusinessException.bad("配置的阈值、时间范围或默认视图无效");
    Set<String> metrics = new HashSet<>();
    defaults().path("cards").forEach(n -> metrics.add(n.path("key").asText()));
    for (String section : List.of("cards", "navigation", "shortcuts")) {
      if (!c.path(section).isArray() || c.path(section).size() > 30)
        throw BusinessException.bad("配置列表格式错误");
      Set<String> keys = new HashSet<>();
      for (JsonNode n : c.path(section)) {
        if (n.path("key").asText().isBlank()
            || !n.path("name").isTextual()
            || !Set.of("app", "chart", "link").contains(n.path("icon").asText())
            || n.path("name").asText().isBlank()
            || n.path("name").asText().length() > 80
            || !n.path("visible").isBoolean()
            || !n.path("order").isInt()
            || !keys.add(n.path("key").asText()))
          throw BusinessException.bad("配置名称、顺序、显示状态或唯一标识无效");
        if (section.equals("cards")) {
          if (!metrics.contains(n.path("metric").asText(n.path("key").asText()))
              || !n.path("days").isInt()
              || !n.path("refreshSeconds").isInt()
              || n.path("days").asInt() < 1
              || n.path("days").asInt() > 365
              || n.path("refreshSeconds").asInt() < 10
              || n.path("refreshSeconds").asInt() > 3600
              || !n.path("color").asText().matches("#[0-9a-fA-F]{6}")
              || !Set.of("DETAIL", "NONE").contains(n.path("clickBehavior").asText()))
            throw BusinessException.bad("指标卡配置无效");
        } else {
          validLink(n.path("path").asText());
          if (section.equals("shortcuts")
              && (!n.path("aiPrompt").isTextual() || n.path("aiPrompt").asText().length() > 2000))
            throw BusinessException.bad("快捷入口提示词格式或长度无效");
          if (section.equals("navigation") && !n.path("parent").isTextual())
            throw BusinessException.bad("导航上级必须是字符串");
        }
      }
    }
    Map<String, String> parents = new HashMap<>();
    for (JsonNode n : c.path("navigation"))
      parents.put(n.path("key").asText(), n.path("parent").asText(""));
    for (String key : parents.keySet()) {
      Set<String> seen = new HashSet<>();
      String cursor = key;
      while (!cursor.isBlank()) {
        if (!seen.add(cursor) || !parents.containsKey(cursor))
          throw BusinessException.bad("导航上级不存在或形成循环");
        cursor = parents.get(cursor);
      }
    }
    if (c.path("navigation").findValues("path").stream()
        .noneMatch(n -> n.asText().equals("/settings"))) throw BusinessException.bad("必须保留配置入口");
    db.update(
        "INSERT INTO workbench_config(user_key,config_json) VALUES('demo',?) ON DUPLICATE KEY"
            + " UPDATE config_json=VALUES(config_json)",
        c.toString());
    audit.record("CONFIG_UPDATE", "demo", "SUCCESS");
    return c;
  }
}
