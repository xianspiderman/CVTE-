package cn.cvte.scm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.cvte.scm.config.ConfigService;
import cn.cvte.scm.metric.MetricService;
import cn.cvte.scm.todo.mapper.TodoMapper;
import cn.cvte.scm.todo.model.*;
import cn.cvte.scm.todo.service.TodoReadService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OverdueTrendTest {
  Todo row(String owner, String due, String status) {
    var t = new Todo();
    t.owner = owner;
    t.createdAt = LocalDateTime.parse("2026-01-01T00:00:00");
    t.dueAt = LocalDateTime.parse(due);
    t.status = status;
    return t;
  }

  MetricService service() {
    var mapper = mock(TodoMapper.class);
    var config = mock(ConfigService.class);
    when(config.threshold()).thenReturn(24);
    when(mapper.all())
        .thenReturn(
            List.of(
                row("A", "2026-11-11T00:00:00", "PENDING"),
                row("B", "2026-11-16T23:59:00", "PENDING"),
                row("A", "2026-11-10T23:59:59", "PENDING"),
                row("A", "2026-11-17T12:00:00", "PENDING"),
                row("A", "2026-11-15T00:00:00", "COMPLETED")));
    return new MetricService(
        new TodoReadService(
            mapper,
            Clock.fixed(Instant.parse("2026-11-17T04:00:00Z"), ZoneId.of("Asia/Shanghai")),
            config));
  }

  @Test
  void usesDueDateNotCreationAndExcludesCompletedAndExactNow() {
    var f = new TodoFilter();
    f.days = 7;
    var result = service().overdueTrend(f, 7);
    assertThat(result.get("total")).isEqualTo(2);
    var trend = (List<Map<String, Object>>) result.get("trend");
    assertThat(trend.get(0)).containsEntry("date", "2026-11-11").containsEntry("count", 1L);
    assertThat(trend.get(5)).containsEntry("count", 1L);
    assertThat(f.days).isNull();
    assertThat(f.dueDays).isEqualTo(7);
  }

  @Test
  void ownerConditionIsPreserved() {
    var f = new TodoFilter();
    f.owner = "A";
    assertThat(service().overdueTrend(f, 7).get("total")).isEqualTo(1);
  }

  @Test
  void rejectsUnsupportedWindow() {
    assertThatThrownBy(() -> service().overdueTrend(new TodoFilter(), 8))
        .hasMessageContaining("7或30");
  }
}
