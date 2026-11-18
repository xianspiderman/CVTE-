package cn.cvte.scm.ai;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.service.SourceService;
import cn.cvte.scm.metric.MetricService;
import cn.cvte.scm.systemlink.LinkService;
import cn.cvte.scm.todo.model.Todo;
import cn.cvte.scm.todo.model.TodoFilter;
import cn.cvte.scm.todo.service.TodoReadService;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class AiTools {
  static final String TODO_SUMMARY = "get_todo_summary";
  static final String SEARCH = "search_todos";
  static final String METRIC = "get_metric";
  static final String LINK = "get_system_link";
  static final String UPDATE = "update_workbench_config";
  static final String SUMMARY = "generate_business_summary";
  static final String PROPOSE = "propose_workbench_config";
  private static final Set<String> BUSINESS_TYPES = Set.of("PROCUREMENT", "INVENTORY", "SUPPLIER");
  private static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH");
  private static final Set<String> STATUSES =
      Set.of("PENDING", "PENDING_REPLY", "PENDING_REVIEW", "COMPLETED");
  private static final List<String> COUNTS =
      List.of("TOTAL", "TODAY_NEW", "TODAY_DUE", "OVERDUE", "HIGH_RISK");
  private static final String VIEWS =
      "ALL, TOTAL, MINE, TODAY_NEW, TODAY_DUE, OVERDUE, DUE_SOON, HIGH_RISK, COMPLETED,"
          + " PENDING_REVIEW, PENDING_REPLY, WEEK_COMPLETED";

  static final List<ToolSpecification> SPECS =
      List.of(
          tool(TODO_SUMMARY, "汇总待办总数、今日新增、今日到期、逾期和高风险。问我的待办填 owner=演示用户。", filters(false)),
          tool(
              SEARCH,
              "分页查询待办明细。按视图与其他条件组合筛选；供应商未回复使用 SUPPLIER 和 PENDING_REPLY。",
              filters(true)
                  .addStringProperty("view", "可用视图：" + VIEWS)
                  .addIntegerProperty("page", "页码从 1 开始，每页 10 条")),
          tool(
              METRIC,
              "查询指标和趋势。最近N天逾期量或逾期趋势必须用 OVERDUE_TREND 和 trendDays，按到期日期统计当前未完成逾期记录；TREND仅用于新增创建趋势。",
              filters(true)
                  .addStringProperty(
                      "metric",
                      "可用指标："
                          + VIEWS
                          + ", AVG_HOURS, ON_TIME_RATE, WEEK_ON_TIME_RATE, TREND, OVERDUE_TREND")
                  .addIntegerProperty("trendDays", "趋势点数 7 或 30，默认 7")
                  .addStringProperty("view", "趋势范围，可用视图：" + VIEWS)
                  .required("metric")),
          tool(
              LINK,
              "返回原系统深链接。询价用 pageCode=rfq，待审核填 status=PENDING_REVIEW。"
                  + "无单号时按页面与筛选条件返回候选记录，使用真实业务单据。",
              JsonObjectSchema.builder()
                  .addIntegerProperty("taskId", "精确待办 ID，可选")
                  .addStringProperty("businessNo", "精确业务单号，可选")
                  .addStringProperty("pageCode", "原系统页面代码，例如 rfq；可选")
                  .addStringProperty("businessType", "PROCUREMENT, INVENTORY, SUPPLIER")
                  .addStringProperty("status", "待办状态，可选")),
          tool(
              PROPOSE,
              "为 update_workbench_config 生成确认提案，无写操作。用户要求修改卡片时调用。",
              JsonObjectSchema.builder()
                  .addStringProperty("card", "卡片标识、精确名称或指标编码，例如 OVERDUE")
                  .addIntegerProperty("position", "新位置，从 1 开始")
                  .addBooleanProperty("visible", "是否显示")
                  .addStringProperty("name", "新名称，最长 80 字符")
                  .required("card")),
          tool(
              SUMMARY,
              "读取待办摘要和优先项，为今日工作重点、工作摘要或催办话术提供依据。" + "催办用 FOLLOW_UP；只有用户指定负责人时才限制 owner。",
              filters(false).addStringProperty("kind", "FOCUS, WORK_SUMMARY, FOLLOW_UP，默认 FOCUS")));

  record SourceInfo(
      String name, String status, LocalDateTime lastAttempt, LocalDateTime dataUpdatedAt) {}

  record Action(String label, String url) {}

  record TodoItem(
      Long id,
      String businessNo,
      String title,
      String owner,
      String status,
      String riskLevel,
      LocalDateTime dueAt,
      boolean overdue) {}

  record Evidence(
      String tool,
      String label,
      Map<String, Object> conditions,
      Object result,
      LocalDateTime queriedAt,
      List<SourceInfo> sources,
      List<Action> actions) {}

  private final TodoReadService todos;
  private final MetricService metrics;
  private final SourceService sources;
  private final LinkService links;

  AiTools(TodoReadService todos, MetricService metrics, SourceService sources, LinkService links) {
    this.todos = todos;
    this.metrics = metrics;
    this.sources = sources;
    this.links = links;
  }

  private static JsonObjectSchema.Builder filters(boolean detail) {
    var b =
        JsonObjectSchema.builder()
            .addStringProperty("owner", "负责人准确名称，例如 演示用户")
            .addStringProperty("businessType", "PROCUREMENT, INVENTORY, SUPPLIER")
            .addStringProperty("sourceSystem", "来源系统准确名称")
            .addIntegerProperty("days", "按创建时间筛选最近 1 至 365 个自然日");
    if (detail)
      b.addStringProperty("riskLevel", "LOW, MEDIUM, HIGH")
          .addStringProperty("status", "PENDING, PENDING_REVIEW, PENDING_REPLY, COMPLETED")
          .addStringProperty("keyword", "匹配标题、描述或业务单号");
    return b;
  }

  private static ToolSpecification tool(
      String name, String description, JsonObjectSchema.Builder schema) {
    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(schema.build())
        .build();
  }

  Evidence execute(String name, JsonNode args) {
    return switch (name) {
      case TODO_SUMMARY -> todoSummary(args);
      case SEARCH -> search(args);
      case METRIC -> metric(args);
      case LINK -> systemLinks(args);
      case SUMMARY -> businessSummary(args);
      default -> throw BusinessException.bad("工具不可用");
    };
  }

  private Evidence todoSummary(JsonNode args) {
    var filter = filter(args);
    return evidence(
        TODO_SUMMARY,
        "待办汇总",
        conditions(filter),
        counts(filter),
        filter.sourceSystem,
        List.of(
            detail("查看待办", filter, "TOTAL"),
            detail("查看今日到期", filter, "TODAY_DUE"),
            detail("查看逾期", filter, "OVERDUE")));
  }

  private Evidence search(JsonNode args) {
    var filter = filter(args);
    filter.view = view(args, "ALL");
    Integer page = integer(args, "page");
    filter.page = page == null ? 1 : page;
    filter.size = 10;
    var found = todos.page(filter);
    var conditions = conditions(filter);
    conditions.put("page", filter.page);
    return evidence(
        SEARCH,
        "待办明细",
        conditions,
        Map.of("total", found.get("total"), "items", items(found)),
        filter.sourceSystem,
        List.of(detail("查看工作台明细", filter, filter.view)));
  }

  private Evidence metric(JsonNode args) {
    var filter = filter(args);
    var conditions = conditions(filter);
    String key = text(args, "metric", 32);
    if (key == null) throw BusinessException.bad("请指定指标");
    conditions.put("metric", key);
    Object result;
    String view = key;
    if ("OVERDUE_TREND".equals(key)) {
      Integer n = integer(args, "trendDays");
      int days = n == null ? 7 : n;
      result = metrics.overdueTrend(filter, days);
      view = "OVERDUE";
      conditions = conditions(filter);
      conditions.put("metric", key);
      conditions.put("trendDays", days);
    } else if ("TREND".equals(key)) {
      Integer days = integer(args, "trendDays");
      int trendDays = days == null ? 7 : days;
      view = filter.view = view(args, "ALL");
      conditions.put("view", view);
      conditions.put("trendDays", trendDays);
      result = metrics.charts(filter, trendDays);
    } else if ("WEEK_ON_TIME_RATE".equals(key)) {
      view = "WEEK_COMPLETED";
      var values = new LinkedHashMap<String, Object>();
      values.put(key, metrics.weeklyOnTimeRate(filter));
      result = values;
    } else {
      if (Set.of("ALL", "MINE").contains(key)) throw BusinessException.bad("请指定有效指标");
      var values = metrics.summary(filter);
      if (!values.containsKey(key)) throw BusinessException.bad("请选择支持的指标");
      var value = new LinkedHashMap<String, Object>();
      value.put(key, values.get(key));
      result = value;
    }
    return evidence(
        METRIC,
        "指标与趋势",
        conditions,
        result,
        filter.sourceSystem,
        List.of(detail("查看指标对应明细", filter, view)));
  }

  private Evidence businessSummary(JsonNode args) {
    String kind = oneOf(args, "kind", Set.of("FOCUS", "WORK_SUMMARY", "FOLLOW_UP"));
    if (kind == null) kind = "FOCUS";
    var filter = filter(args);
    String view = kind.equals("FOLLOW_UP") ? "PENDING_REPLY" : "OVERDUE";
    filter.view = view;
    filter.size = 5;
    if (kind.equals("FOLLOW_UP") && filter.businessType == null) filter.businessType = "SUPPLIER";
    var counts = counts(filter);
    var found = todos.page(filter);
    var conditions = conditions(filter);
    conditions.put("kind", kind);
    return evidence(
        SUMMARY,
        "业务摘要依据",
        conditions,
        Map.of("counts", counts, "total", found.get("total"), "items", items(found)),
        filter.sourceSystem,
        List.of(detail("查看相关待办", filter, view)));
  }

  private Evidence systemLinks(JsonNode args) {
    var filter = filter(args);
    String pageCode = text(args, "pageCode", 100);
    String number = text(args, "businessNo", 128);
    JsonNode id = args.path("taskId");
    if (id.isMissingNode() || id.isNull()) {
      if (pageCode == null && number == null) throw BusinessException.bad("请提供页面代码、业务单号或待办 ID");
    } else if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() < 1) {
      throw BusinessException.bad("待办 ID 无效");
    }
    if (pageCode != null && !pageCode.matches("[a-zA-Z0-9_-]+"))
      throw BusinessException.bad("页面代码无效");
    var conditions = conditions(filter);
    if (number != null) conditions.put("businessNo", number);
    if (pageCode != null) conditions.put("pageCode", pageCode);
    if (id.isIntegralNumber()) conditions.put("taskId", id.asLong());
    List<Todo> matches;
    if (id.isIntegralNumber()) {
      var row = todos.get(id.asLong());
      matches =
          (number == null || number.equalsIgnoreCase(row.businessNo))
                  && (filter.businessType == null || filter.businessType.equals(row.businessType))
                  && (filter.status == null || filter.status.equals(row.status))
              ? List.of(row)
              : List.of();
    } else {
      matches =
          todos.filtered(filter).stream()
              .filter(t -> number == null || number.equalsIgnoreCase(t.businessNo))
              .filter(t -> number != null || Objects.equals(pageCode, t.sourcePage))
              .toList();
    }
    var actions = new ArrayList<Action>();
    var unavailable = new ArrayList<String>();
    var candidates = matches.stream().limit(5).toList();
    for (var t : candidates) {
      try {
        var link = links.resolve(t.id, pageCode);
        actions.add(new Action("打开 " + t.businessNo, link.get("url")));
      } catch (BusinessException e) {
        unavailable.add(t.businessNo);
      }
    }
    return evidence(
        LINK,
        "原系统入口",
        conditions,
        Map.of(
            "total",
            matches.size(),
            "items",
            candidates.stream().map(AiTools::item).toList(),
            "unavailable",
            unavailable),
        filter.sourceSystem,
        actions);
  }

  private Map<String, Object> counts(TodoFilter f) {
    var all = metrics.summary(f);
    Map<String, Object> result = new LinkedHashMap<>();
    COUNTS.forEach(k -> result.put(k, all.get(k)));
    return result;
  }

  private Evidence evidence(
      String tool,
      String label,
      Map<String, Object> conditions,
      Object result,
      String source,
      List<Action> actions) {
    var updates = sources.dataUpdates();
    var known =
        sources.list().stream()
            .filter(s -> source == null || source.equals(s.name))
            .map(s -> new SourceInfo(s.name, s.syncStatus, s.lastSyncAt, updates.get(s.id)))
            .toList();
    if (known.isEmpty() && source != null)
      known = List.of(new SourceInfo(source, "UNKNOWN", null, null));
    return new Evidence(tool, label, conditions, result, todos.now(), known, actions);
  }

  private List<TodoItem> items(Map<String, Object> page) {
    return ((List<?>) page.get("items")).stream().map(t -> item((Todo) t)).toList();
  }

  private static TodoItem item(Todo t) {
    return new TodoItem(
        t.id, t.businessNo, t.title, t.owner, t.status, t.riskLevel, t.dueAt, t.overdue);
  }

  private static TodoFilter filter(JsonNode args) {
    var f = new TodoFilter();
    f.businessType = oneOf(args, "businessType", BUSINESS_TYPES);
    f.riskLevel = oneOf(args, "riskLevel", RISKS);
    f.status = oneOf(args, "status", STATUSES);
    f.sourceSystem = text(args, "sourceSystem", 100);
    f.owner = text(args, "owner", 100);
    f.keyword = text(args, "keyword", 128);
    f.days = integer(args, "days");
    f.validate();
    return f;
  }

  private static String view(JsonNode args, String fallback) {
    String view = text(args, "view", 32);
    if (view == null) return fallback;
    if (!List.of(VIEWS.split(", ")).contains(view)) throw BusinessException.bad("未知待办视图");
    return view;
  }

  private static Map<String, Object> conditions(TodoFilter f) {
    Map<String, Object> c = new LinkedHashMap<>();
    if (!"ALL".equals(f.view)) c.put("view", f.view);
    if (f.owner != null) c.put("owner", f.owner);
    if (f.businessType != null) c.put("businessType", f.businessType);
    if (f.riskLevel != null) c.put("riskLevel", f.riskLevel);
    if (f.status != null) c.put("status", f.status);
    if (f.sourceSystem != null) c.put("sourceSystem", f.sourceSystem);
    if (f.keyword != null) c.put("keyword", f.keyword);
    if (f.days != null) c.put("days", f.days);
    if (f.dueDays != null) c.put("dueDays", f.dueDays);
    return c;
  }

  private static Action detail(String label, TodoFilter f, String view) {
    var params = conditions(f);
    params.put("view", view);
    // The dashboard applies configured owner/time defaults unless explicitly cleared.
    params.putIfAbsent("owner", "");
    params.putIfAbsent("days", "");
    params.keySet().removeAll(Set.of("page", "metric", "kind", "trendDays"));
    return new Action(
        label,
        "/?"
            + params.entrySet().stream()
                .map(
                    e ->
                        e.getKey()
                            + "="
                            + URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
  }

  static String text(JsonNode args, String field, int max) {
    JsonNode value = args.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max)
      throw BusinessException.bad("参数 " + field + " 格式或长度无效");
    return value.asText().strip();
  }

  static Integer integer(JsonNode args, String field) {
    JsonNode value = args.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isIntegralNumber() || !value.canConvertToInt())
      throw BusinessException.bad("参数 " + field + " 须为整数");
    return value.asInt();
  }

  static Boolean bool(JsonNode args, String field) {
    JsonNode value = args.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isBoolean()) throw BusinessException.bad("参数 " + field + " 须为布尔值");
    return value.asBoolean();
  }

  private static String oneOf(JsonNode args, String field, Set<String> allowed) {
    String value = text(args, field, 32);
    if (value != null && !allowed.contains(value))
      throw BusinessException.bad("参数 " + field + " 不受支持");
    return value;
  }
}
