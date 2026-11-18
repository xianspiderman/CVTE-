package cn.cvte.scm.metric;

import cn.cvte.scm.todo.model.*;
import cn.cvte.scm.todo.service.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MetricService {
  private final TodoReadService todos;

  public MetricService(TodoReadService t) {
    todos = t;
  }

  public Map<String, Object> summary(TodoFilter f) {
    Map<String, Object> result = new LinkedHashMap<>();
    String old = f.view;
    var snapshot = todos.snapshot();
    for (String key :
        List.of(
            "TOTAL",
            "TODAY_NEW",
            "TODAY_DUE",
            "OVERDUE",
            "DUE_SOON",
            "HIGH_RISK",
            "PENDING_REVIEW",
            "PENDING_REPLY",
            "WEEK_COMPLETED",
            "COMPLETED")) {
      f.view = key;
      result.put(key, todos.filtered(f, snapshot).size());
    }
    f.view = "COMPLETED";
    var complete = todos.filtered(f, snapshot);
    result.put(
        "AVG_HOURS",
        complete.isEmpty()
            ? null
            : Math.round(
                    complete.stream()
                            .mapToLong(
                                t -> Duration.between(t.createdAt, t.completedAt).toMinutes())
                            .average()
                            .orElse(0)
                        / 60
                        * 100)
                / 100.0);
    result.put("ON_TIME_RATE", onTimeRate(complete));
    f.view = old;
    return result;
  }

  public Double weeklyOnTimeRate(TodoFilter filter) {
    filter.view = "WEEK_COMPLETED";
    return onTimeRate(todos.filtered(filter));
  }

  private Double onTimeRate(List<Todo> complete) {
    return complete.isEmpty()
        ? null
        : Math.round(
                10000.0
                    * complete.stream().filter(t -> !t.completedAt.isAfter(t.dueAt)).count()
                    / complete.size())
            / 100.0;
  }

  public Map<String, Object> overdueTrend(TodoFilter f, int days) {
    if (days != 7 && days != 30) throw cn.cvte.scm.common.BusinessException.bad("趋势仅支持7或30天");
    f.view = "OVERDUE";
    f.days = null;
    f.dueDays = days;
    var snapshot = todos.snapshot();
    var rows = todos.filtered(f, snapshot);
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = days - 1; i >= 0; i--) {
      var date = snapshot.now().toLocalDate().minusDays(i);
      trend.add(
          Map.of(
              "date",
              date.toString(),
              "count",
              rows.stream().filter(t -> t.dueAt.toLocalDate().equals(date)).count()));
    }
    return Map.of(
        "trend",
        trend,
        "total",
        rows.size(),
        "trendMeaning",
        "最近N天到期且当前仍未完成的逾期量，按要求完成日期分布；不是创建趋势，也不是历史每日逾期存量快照");
  }

  public Map<String, Object> charts(TodoFilter f, int days) {
    if (days != 7 && days != 30) throw cn.cvte.scm.common.BusinessException.bad("趋势仅支持7或30天");
    var snapshot = todos.snapshot();
    var rows = todos.filtered(f, snapshot);
    var today = snapshot.now().toLocalDate();
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = days - 1; i >= 0; i--) {
      var date = today.minusDays(i);
      trend.add(
          Map.of(
              "date",
              date.toString(),
              "count",
              rows.stream().filter(t -> t.createdAt.toLocalDate().equals(date)).count()));
    }
    return Map.of(
        "trend",
        trend,
        "business",
        rows.stream()
            .collect(
                Collectors.groupingBy(t -> t.businessType, TreeMap::new, Collectors.counting())),
        "risk",
        rows.stream()
            .collect(Collectors.groupingBy(t -> t.riskLevel, TreeMap::new, Collectors.counting())),
        "trendMeaning",
        "按创建日期的任务数量（非历史逾期快照）");
  }
}
