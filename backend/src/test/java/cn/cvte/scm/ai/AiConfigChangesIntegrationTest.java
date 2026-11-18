package cn.cvte.scm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.config.ConfigService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SCM_DB_TEST", matches = "true")
class AiConfigChangesIntegrationTest {
  @Autowired AiConfigChanges changes;
  @Autowired ConfigService configs;
  @Autowired JdbcTemplate db;

  @Test
  void proposalIsReadOnlyAndApplyingWritesConfigAndAudits() {
    configs.save(configs.defaults());
    var before = configs.get();
    var proposal = changes.propose("OVERDUE", 1, false, "待处理逾期");
    assertThat(configs.get()).isEqualTo(before);
    assertThat(proposal.preview().changes()).hasSize(3);
    int logCount = appliedCount();
    changes.apply(proposal, System.nanoTime());
    assertThat(configs.get()).isEqualTo(proposal.after());
    assertThat(appliedCount()).isEqualTo(logCount + 1);
  }

  @Test
  void interveningManualUpdateInvalidatesProposal() {
    configs.save(configs.defaults());
    var proposal = changes.propose("OVERDUE", 1, null, null);
    var manual = (ObjectNode) configs.get();
    manual.put("dueSoonHours", 36);
    configs.save(manual);
    int logCount = appliedCount();
    assertThatThrownBy(() -> changes.apply(proposal, System.nanoTime()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("配置已变化");
    assertThat(configs.get()).isEqualTo(manual);
    assertThat(appliedCount()).isEqualTo(logCount);
  }

  private int appliedCount() {
    return db.queryForObject(
        "SELECT COUNT(*) FROM ai_tool_log WHERE tool_name='update_workbench_config'"
            + " AND JSON_UNQUOTE(JSON_EXTRACT(result,'$.status'))='APPLIED'",
        Integer.class);
  }
}
