package cn.cvte.scm.todo.service;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.config.ConfigService;
import cn.cvte.scm.todo.mapper.TodoMapper;
import cn.cvte.scm.todo.model.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TodoReadService {
  private final TodoMapper mapper;
  private final Clock clock;
  private final ConfigService config;

  public TodoReadService(TodoMapper m, Clock c, ConfigService config) {
    mapper = m;
    clock = c;
    this.config = config;
  }

  public long storedCount() {
    return mapper.count();
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public record Snapshot(List<Todo> rows, LocalDateTime now, int threshold) {}

  public Snapshot snapshot() {
    return new Snapshot(mapper.all(), now(), config.threshold());
  }

  public List<Todo> filtered(TodoFilter f) {
    return filtered(f, snapshot());
  }

  public List<Todo> filtered(TodoFilter f, Snapshot snapshot) {
    f.validate();
    var now = snapshot.now();
    int threshold = snapshot.threshold();
    return snapshot.rows().stream()
        .filter(t -> TodoRules.matches(t, f, now, threshold))
        .peek(
            t -> {
              t.overdue = TodoRules.overdue(t, now);
              t.overdueMinutes = t.overdue ? Duration.between(t.dueAt, now).toMinutes() : 0;
            })
        .toList();
  }

  public Map<String, Object> page(TodoFilter f) {
    var snapshot = snapshot();
    var rows = filtered(f, snapshot);
    int from =
        Math.min(rows.size(), (int) Math.min(Integer.MAX_VALUE, (long) (f.page - 1) * f.size));
    return Map.of(
        "items",
        rows.subList(from, Math.min(rows.size(), from + f.size)),
        "total",
        rows.size(),
        "now",
        snapshot.now(),
        "timezone",
        "Asia/Shanghai");
  }

  public Todo get(long id) {
    Todo t = mapper.get(id);
    if (t == null) throw BusinessException.missing();
    var now = now();
    t.overdue = TodoRules.overdue(t, now);
    t.overdueMinutes = t.overdue ? Duration.between(t.dueAt, now).toMinutes() : 0;
    return t;
  }
}
