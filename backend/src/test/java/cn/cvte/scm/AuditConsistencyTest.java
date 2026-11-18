package cn.cvte.scm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.config.ConfigService;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.service.SourceService;
import cn.cvte.scm.systemlink.LinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SCM_DB_TEST", matches = "true")
class AuditConsistencyTest {
  @Autowired SourceService sources;
  @Autowired ConfigService configs;
  @Autowired LinkService links;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean AuditStore audit;

  void failAudit() {
    doThrow(new IllegalStateException("simulated audit storage failure"))
        .when(audit)
        .record(anyString(), anyString(), anyString());
  }

  @Test
  void sourceAndAuditAreAtomic() {
    Source s = new Source();
    s.name = "quality-audit-" + UUID.randomUUID();
    s.type = "FILE";
    s.mappingConfig = json.createObjectNode();
    failAudit();
    assertThatThrownBy(() -> sources.save(s)).hasMessageContaining("simulated");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM data_source WHERE name=?", Integer.class, s.name))
        .isZero();
  }

  @Test
  void templateAndAuditAreAtomic() {
    long id = db.queryForObject("SELECT MIN(id) FROM data_source", Long.class);
    String page = "quality-" + UUID.randomUUID();
    failAudit();
    assertThatThrownBy(() -> links.save(id, page, "http://127.0.0.1:4010/business/test"))
        .hasMessageContaining("simulated");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM system_link_template WHERE source_id=? AND page_code=?",
                Integer.class,
                id,
                page))
        .isZero();
  }

  @Test
  void configAndAuditAreAtomic() {
    var before = configs.get();
    var changed = (ObjectNode) before.deepCopy();
    changed.put("dueSoonHours", before.path("dueSoonHours").asInt() == 48 ? 24 : 48);
    failAudit();
    assertThatThrownBy(() -> configs.save(changed)).hasMessageContaining("simulated");
    assertThat(configs.get()).isEqualTo(before);
  }
}
