package cn.cvte.scm.integration.service;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.adapter.InputRow;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.todo.model.Todo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TodoConverter {
  private final ObjectMapper json;

  public TodoConverter(ObjectMapper j) {
    json = j;
  }

  public Todo convert(Source s, InputRow row) {
    if (row.error() != null) throw BusinessException.bad(row.error());
    Todo t = new Todo();
    t.sourceId = s.id;
    t.sourceRecordId = value(s, row, "sourceRecordId", 128, true);
    t.businessNo = value(s, row, "businessNo", 128, true);
    t.title = value(s, row, "title", 255, true);
    t.description = value(s, row, "description", 4000, false);
    t.businessType =
        choice(
            value(s, row, "businessType", 32, true),
            "businessType",
            "PROCUREMENT",
            "INVENTORY",
            "SUPPLIER");
    t.status =
        choice(
            value(s, row, "status", 32, true),
            "status",
            "PENDING",
            "PENDING_REVIEW",
            "PENDING_REPLY",
            "COMPLETED");
    t.riskLevel =
        choice(value(s, row, "riskLevel", 20, true), "riskLevel", "LOW", "MEDIUM", "HIGH");
    t.priority = choice(value(s, row, "priority", 20, true), "priority", "LOW", "MEDIUM", "HIGH");
    t.owner = value(s, row, "owner", 100, true);
    t.sourcePage = value(s, row, "sourcePage", 100, true);
    t.businessOrg = value(s, row, "businessOrg", 100, true);
    t.businessUrl = value(s, row, "businessUrl", 1000, false);
    if (!t.businessUrl.isBlank()) cn.cvte.scm.config.ConfigService.validLink(t.businessUrl);
    t.createdAt = date(value(s, row, "createdAt", 50, true), "createdAt");
    t.dueAt = date(value(s, row, "dueAt", 50, true), "dueAt");
    t.sourceUpdatedAt = date(value(s, row, "sourceUpdatedAt", 50, true), "sourceUpdatedAt");
    if (t.sourceUpdatedAt.isBefore(t.createdAt))
      throw BusinessException.bad("sourceUpdatedAt 不得早于 createdAt");
    String done = value(s, row, "completedAt", 50, false);
    t.completedAt = done.isBlank() ? null : date(done, "completedAt");
    if ("COMPLETED".equals(t.status)
        && (t.completedAt == null
            || t.completedAt.isBefore(t.createdAt)
            || t.completedAt.isAfter(t.sourceUpdatedAt)))
      throw BusinessException.bad("已完成任务必须有合法 completedAt，且在创建和来源更新之间");
    if (!"COMPLETED".equals(t.status) && t.completedAt != null)
      throw BusinessException.bad("未完成任务不得填写 completedAt");
    String extra = value(s, row, "extraJson", 16000, false);
    try {
      var node = json.readTree(extra.isBlank() ? "{}" : extra);
      if (!node.isObject()) throw new Exception();
      t.extraJson = node.toString();
    } catch (Exception e) {
      throw BusinessException.bad("extraJson 必须为 JSON 对象");
    }
    return t;
  }

  private String value(Source s, InputRow r, String key, int max, boolean required) {
    String source = s.mappingConfig.path(key).asText(key);
    String value = r.values().getOrDefault(source, "").trim();
    if (required && value.isEmpty() || value.length() > max)
      throw BusinessException.bad(key + " 缺失或超过长度限制 " + max);
    return value;
  }

  private String choice(String v, String field, String... options) {
    if (!List.of(options).contains(v)) throw BusinessException.bad(field + " 枚举不合法");
    return v;
  }

  public static LocalDateTime date(String s, String field) {
    try {
      try {
        return OffsetDateTime.parse(s)
            .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
            .toLocalDateTime()
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      } catch (Exception e) {
        return LocalDateTime.parse(s.replace(' ', 'T'))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      }
    } catch (Exception e) {
      throw BusinessException.bad(field + " 日期须为 ISO-8601，默认时区 Asia/Shanghai");
    }
  }
}
