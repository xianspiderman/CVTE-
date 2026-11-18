package cn.cvte.scm.todo.service;

import cn.cvte.scm.todo.model.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

public final class TodoRules {
  private TodoRules() {}

  public static boolean open(Todo t) {
    return !"COMPLETED".equals(t.status);
  }

  public static boolean overdue(Todo t, LocalDateTime now) {
    return open(t) && t.dueAt.isBefore(now);
  }

  public static boolean soon(Todo t, LocalDateTime now, int hours) {
    return open(t) && !t.dueAt.isBefore(now) && t.dueAt.isBefore(now.plusHours(hours));
  }

  public static boolean matches(Todo t, TodoFilter f, LocalDateTime now, int hours) {
    if (f.businessType != null
            && !f.businessType.isBlank()
            && !f.businessType.equals(t.businessType)
        || f.sourceSystem != null
            && !f.sourceSystem.isBlank()
            && !f.sourceSystem.equals(t.sourceSystem)
        || f.riskLevel != null && !f.riskLevel.isBlank() && !f.riskLevel.equals(t.riskLevel)
        || f.owner != null && !f.owner.isBlank() && !f.owner.equals(t.owner)
        || f.status != null && !f.status.isBlank() && !f.status.equals(t.status)) return false;
    if (f.keyword != null
        && !f.keyword.isBlank()
        && !(t.title + " " + t.businessNo + " " + t.description)
            .toLowerCase()
            .contains(f.keyword.toLowerCase())) return false;
    if (f.dueDate != null && !t.dueAt.toLocalDate().equals(f.dueDate)) return false;
    if (f.dueDays != null
        && (t.dueAt.isBefore(now.toLocalDate().minusDays(f.dueDays - 1).atStartOfDay())
            || !t.dueAt.isBefore(now))) return false;
    if (f.createdDate != null && !t.createdAt.toLocalDate().equals(f.createdDate)) return false;
    if (f.days != null
        && (t.createdAt.isBefore(now.toLocalDate().minusDays(f.days - 1).atStartOfDay())
            || t.createdAt.isAfter(now))) return false;
    return switch (f.view) {
      case "TOTAL" -> open(t);
      case "MINE" -> open(t) && "演示用户".equals(t.owner);
      case "TODAY_NEW" -> t.createdAt.toLocalDate().equals(now.toLocalDate());
      case "TODAY_DUE" -> open(t) && t.dueAt.toLocalDate().equals(now.toLocalDate());
      case "OVERDUE" -> overdue(t, now);
      case "DUE_SOON" -> soon(t, now, hours);
      case "HIGH_RISK" -> open(t) && "HIGH".equals(t.riskLevel);
      case "PENDING_REVIEW", "PENDING_REPLY" -> f.view.equals(t.status);
      case "COMPLETED", "AVG_HOURS", "ON_TIME_RATE" -> !open(t);
      case "WEEK_COMPLETED" -> !open(t)
          && !t.completedAt.isBefore(
              now.toLocalDate()
                  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                  .atStartOfDay())
          && !t.completedAt.isAfter(now);
      default -> true;
    };
  }
}
