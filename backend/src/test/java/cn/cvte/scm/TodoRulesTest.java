package cn.cvte.scm;

import static org.assertj.core.api.Assertions.*;

import cn.cvte.scm.todo.model.*;
import cn.cvte.scm.todo.service.TodoRules;
import java.time.*;
import org.junit.jupiter.api.Test;

class TodoRulesTest {
  final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);

  Todo task() {
    Todo t = new Todo();
    t.status = "PENDING";
    t.createdAt = now.minusDays(1);
    t.dueAt = now;
    t.owner = "演示用户";
    t.businessType = "PROCUREMENT";
    t.riskLevel = "HIGH";
    return t;
  }

  @Test
  void exactDeadlineIsSoonNotOverdue() {
    Todo t = task();
    assertThat(TodoRules.overdue(t, now)).isFalse();
    assertThat(TodoRules.soon(t, now, 24)).isTrue();
  }

  @Test
  void thresholdUpperBoundIsExclusive() {
    Todo t = task();
    t.dueAt = now.plusHours(24);
    assertThat(TodoRules.soon(t, now, 24)).isFalse();
    assertThat(TodoRules.soon(t, now, 25)).isTrue();
  }

  @Test
  void completedTasksNeverOverdue() {
    Todo t = task();
    t.dueAt = now.minusDays(1);
    t.status = "COMPLETED";
    assertThat(TodoRules.overdue(t, now)).isFalse();
    assertThat(TodoRules.soon(t, now, 24)).isFalse();
  }

  @Test
  void combinesOwnerTypeRiskAndOverdue() {
    Todo t = task();
    t.dueAt = now.minusNanos(1);
    TodoFilter f = new TodoFilter();
    f.view = "OVERDUE";
    f.owner = "演示用户";
    f.businessType = "PROCUREMENT";
    f.riskLevel = "HIGH";
    assertThat(TodoRules.matches(t, f, now, 24)).isTrue();
    f.owner = "其他人";
    assertThat(TodoRules.matches(t, f, now, 24)).isFalse();
  }

  @Test
  void todayBoundaryUsesLocalDate() {
    Todo t = task();
    TodoFilter f = new TodoFilter();
    f.view = "TODAY_NEW";
    t.createdAt = now.toLocalDate().atStartOfDay();
    assertThat(TodoRules.matches(t, f, now, 24)).isTrue();
    t.createdAt = t.createdAt.minusNanos(1);
    assertThat(TodoRules.matches(t, f, now, 24)).isFalse();
  }

  @Test
  void invalidPaginationAndViewRejected() {
    TodoFilter f = new TodoFilter();
    f.size = 0;
    assertThatThrownBy(f::validate).hasMessageContaining("分页");
    f.size = 20;
    f.view = "UNKNOWN";
    assertThatThrownBy(f::validate).hasMessageContaining("视图");
  }
}
