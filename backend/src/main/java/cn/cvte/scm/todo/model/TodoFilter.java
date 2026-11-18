package cn.cvte.scm.todo.model;

import cn.cvte.scm.common.BusinessException;
import java.time.LocalDate;

public class TodoFilter {
  public String view = "ALL", businessType, sourceSystem, riskLevel, owner, keyword, status;
  public LocalDate createdDate, dueDate;
  public Integer days, dueDays;
  public int page = 1, size = 20;

  public void validate() {
    if (dueDays != null && (dueDays < 1 || dueDays > 365)) throw BusinessException.bad("到期时间范围不合法");
    if (page < 1 || size < 1 || size > 200 || days != null && (days < 1 || days > 365))
      throw BusinessException.bad("分页或时间范围不合法");
    if (!java.util.Set.of(
            "ALL",
            "TOTAL",
            "MINE",
            "TODAY_NEW",
            "TODAY_DUE",
            "OVERDUE",
            "DUE_SOON",
            "HIGH_RISK",
            "COMPLETED",
            "PENDING_REVIEW",
            "PENDING_REPLY",
            "WEEK_COMPLETED",
            "AVG_HOURS",
            "ON_TIME_RATE")
        .contains(view)) throw BusinessException.bad("未知待办视图");
  }
}
