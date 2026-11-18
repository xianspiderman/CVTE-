package cn.cvte.scm.ai;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.config.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AiConfigChanges {
  record Change(String item, String field, String before, String after) {}

  record Preview(UUID id, List<Change> changes, Instant expiresAt) {}

  record Proposal(Preview preview, JsonNode before, JsonNode after) {}

  private final ConfigService configs;
  private final AiAudit audit;

  AiConfigChanges(ConfigService configs, AiAudit audit) {
    this.configs = configs;
    this.audit = audit;
  }

  Proposal propose(String card, Integer position, Boolean visible, String name) {
    if (card == null || position == null && visible == null && name == null)
      throw BusinessException.bad("请提供要修改的卡片以及目标位置、显示状态或名称");
    JsonNode current = configs.get();
    JsonNode updated = current.deepCopy();
    List<ObjectNode> cards = new ArrayList<>();
    updated.path("cards").forEach(n -> cards.add((ObjectNode) n));
    cards.sort(Comparator.comparingInt(n -> n.path("order").asInt()));
    ObjectNode selected =
        cards.stream()
            .filter(n -> n.path("key").asText().equalsIgnoreCase(card))
            .findFirst()
            .orElseGet(() -> uniqueCard(updated.path("cards"), card));

    List<Change> changes = new ArrayList<>();
    String label = selected.path("name").asText();
    if (position != null) {
      if (position < 1 || position > cards.size()) throw BusinessException.bad("卡片位置超出范围");
      int from = cards.indexOf(selected);
      if (from != position - 1) {
        cards.remove(from);
        cards.add(position - 1, selected);
        for (int i = 0; i < cards.size(); i++) cards.get(i).put("order", i);
        changes.add(new Change(label, "位置", String.valueOf(from + 1), position.toString()));
      }
    }
    if (visible != null && visible != selected.path("visible").asBoolean()) {
      changes.add(new Change(label, "显示状态", visible ? "隐藏" : "显示", visible ? "显示" : "隐藏"));
      selected.put("visible", visible);
    }
    if (name != null && !name.equals(label)) {
      if (name.isBlank() || name.length() > 80) throw BusinessException.bad("卡片名称须为 1～80 字符");
      changes.add(new Change(label, "名称", label, name));
      selected.put("name", name);
    }
    if (changes.isEmpty()) throw BusinessException.bad("卡片已经符合请求的配置");
    return new Proposal(
        new Preview(UUID.randomUUID(), List.copyOf(changes), Instant.now().plusSeconds(300)),
        current,
        updated);
  }

  private ObjectNode uniqueCard(JsonNode cards, String card) {
    var matches =
        StreamSupport.stream(cards.spliterator(), false)
            .filter(
                n ->
                    n.path("name").asText().equals(card)
                        || n.path("metric").asText().equalsIgnoreCase(card))
            .toList();
    if (matches.size() != 1) throw BusinessException.bad("无法唯一定位卡片，请提供卡片名称或标识");
    return (ObjectNode) matches.get(0);
  }

  @Transactional
  public void apply(Proposal proposal, long started) {
    configs.saveIfUnchanged(proposal.before(), proposal.after());
    audit.record("update_workbench_config", null, "APPLIED", started, null);
  }
}
