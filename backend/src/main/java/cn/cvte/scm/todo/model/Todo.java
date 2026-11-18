package cn.cvte.scm.todo.model;

import java.time.LocalDateTime;

public class Todo {
  public Long id, sourceId;
  public String sourceRecordId,
      businessNo,
      title,
      description,
      businessType,
      sourceSystem,
      sourcePage,
      status,
      riskLevel,
      priority,
      owner,
      businessOrg,
      businessUrl,
      extraJson;
  public LocalDateTime createdAt, dueAt, sourceUpdatedAt, updatedAt, completedAt;
  public boolean overdue;
  public long overdueMinutes;
}
