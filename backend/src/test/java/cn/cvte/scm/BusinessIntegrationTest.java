package cn.cvte.scm;

import static org.assertj.core.api.Assertions.*;

import cn.cvte.scm.config.ConfigService;
import cn.cvte.scm.integration.adapter.*;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.service.*;
import cn.cvte.scm.systemlink.LinkService;
import cn.cvte.scm.todo.mapper.TodoMapper;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SCM_DB_TEST", matches = "true")
class BusinessIntegrationTest {
  @Autowired ObjectMapper json;
  @Autowired SourceService sources;
  @Autowired ImportService imports;
  @Autowired FileAdapter files;
  @Autowired TodoConverter converter;
  @Autowired TodoMapper todos;
  @Autowired ConfigService configs;
  @Autowired LinkService links;
  @Autowired JdbcTemplate db;
  Source source;
  List<InputRow> rows;

  @BeforeEach
  void setup() throws Exception {
    source = new Source();
    source.name = "integration-test-" + UUID.randomUUID();
    source.type = "FILE";
    source.mappingConfig = json.createObjectNode();
    source = sources.save(source);
    rows =
        files.read(
            new MockMultipartFile(
                "file",
                "acceptance.csv",
                "text/csv",
                Files.readAllBytes(Path.of("../test-data/acceptance.csv"))));
  }

  @Test
  void partialImportAndIdempotence() {
    var first = imports.ingest(source, "acceptance.csv", rows);
    assertThat(((Number) first.get("success_count")).intValue()).isEqualTo(40);
    assertThat(((Number) first.get("failure_count")).intValue()).isEqualTo(3);
    assertThat((List<?>) first.get("errors")).hasSize(3);
    var second = imports.ingest(source, "acceptance.csv", rows);
    assertThat(((Number) second.get("duplicate_count")).intValue()).isEqualTo(40);
    assertThat(((Number) second.get("success_count")).intValue()).isZero();
  }

  @Test
  void newerUpdateWinsOldVersionSkipped() {
    InputRow row = rows.get(0);
    imports.ingest(source, "first", List.of(row));
    Map<String, String> change = new HashMap<>(row.values());
    change.put("title", "更新标题");
    change.put("sourceUpdatedAt", "2026-11-18T12:00:00");
    var result = imports.ingest(source, "update", List.of(new InputRow(2, change, null)));
    assertThat(((Number) result.get("updated_count")).intValue()).isEqualTo(1);
    imports.ingest(source, "old", List.of(row));
    assertThat(todos.existing(source.id, row.values().get("sourceRecordId")).title)
        .isEqualTo("更新标题");
  }

  @Test
  void mappingTimezoneAndInvalidFields() throws Exception {
    source.mappingConfig = json.readTree("{\"sourceRecordId\":\"externalId\"}");
    Map<String, String> m = new HashMap<>(rows.get(0).values());
    m.put("externalId", m.remove("sourceRecordId"));
    m.put("dueAt", "2026-11-17T04:00:00Z");
    assertThat(converter.convert(source, new InputRow(2, m, null)).dueAt.getHour()).isEqualTo(12);
    m.put("status", "unknown");
    assertThatThrownBy(() -> converter.convert(source, new InputRow(2, m, null)))
        .hasMessageContaining("status");
  }

  @Test
  void excelImportsAndFormulaRowsAreVisibleErrors() throws Exception {
    try (var book = new XSSFWorkbook();
        var bytes = new ByteArrayOutputStream()) {
      var sheet = book.createSheet("todos");
      var headers = new ArrayList<>(rows.get(0).values().keySet());
      var h = sheet.createRow(0);
      for (int k = 0; k < headers.size(); k++) h.createCell(k).setCellValue(headers.get(k));
      for (int i = 0; i < 2; i++) {
        var r = sheet.createRow(i + 1);
        for (int k = 0; k < headers.size(); k++)
          r.createCell(k).setCellValue(rows.get(i).values().get(headers.get(k)));
      }
      sheet.getRow(2).getCell(0).setCellFormula("1+1");
      book.write(bytes);
      Files.createDirectories(Path.of("target/test-fixtures"));
      Files.write(Path.of("target/test-fixtures/acceptance.xlsx"), bytes.toByteArray());
      var parsed =
          files.read(
              new MockMultipartFile(
                  "file", "sample.xlsx", "application/octet-stream", bytes.toByteArray()));
      assertThat(parsed).hasSize(2);
      var result = imports.ingest(source, "sample.xlsx", parsed);
      assertThat(((Number) result.get("success_count")).intValue()).isEqualTo(1);
      assertThat(((Number) result.get("failure_count")).intValue()).isEqualTo(1);
    }
  }

  @Test
  void unsafeUrlsAndConfigRollback() {
    source.type = "HTTP";
    source.endpoint = "http://example.com/api/private";
    assertThatThrownBy(() -> sources.save(source)).hasMessageContaining("本轮数据源");
    var config = configs.get().deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("dueSoonHours", 48);
    configs.save(config);
    assertThat(configs.threshold()).isEqualTo(48);
    ((com.fasterxml.jackson.databind.node.ObjectNode) config.path("navigation").get(0))
        .put("path", "javascript:alert(1)");
    assertThatThrownBy(() -> configs.save(config)).hasMessageContaining("HTTP");
    assertThat(configs.get().path("navigation").get(0).path("path").asText())
        .isNotEqualTo("javascript:alert(1)");
  }

  @Test
  void deepLinkEscapesAllVariablesAndLogs() {
    imports.ingest(source, "one", List.of(rows.get(0)));
    var t = todos.existing(source.id, rows.get(0).values().get("sourceRecordId"));
    links.save(
        source.id,
        t.sourcePage,
        "http://127.0.0.1:4010/business/${pageCode}?no=${businessNo}&id=${taskId}&source=${sourceSystem}");
    String url = links.resolve(t.id, null).get("url");
    assertThat(url)
        .doesNotContain("${")
        .contains("id=" + t.id)
        .contains("source=integration-test-");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type='SYSTEM_JUMP' AND"
                    + " target=?",
                Integer.class,
                t.id + ":" + t.sourcePage))
        .isEqualTo(1);
    assertThatThrownBy(() -> links.save(source.id, "bad", "javascript:alert(1)"))
        .hasMessageContaining("HTTP");
  }

  @Test
  void corruptFileAndMissingHeaderRejected() {
    assertThatThrownBy(
            () ->
                files.read(
                    new MockMultipartFile(
                        "file", "x.xlsx", "application/octet-stream", new byte[] {1, 2, 3})))
        .hasMessageContaining("格式");
    assertThatThrownBy(
            () ->
                files.read(
                    new MockMultipartFile("file", "x.csv", "text/csv", "a,a\nx,y".getBytes())))
        .hasMessageContaining("格式");
  }

  @Test
  void fractionalTimestampsRemainIdempotent() {
    Map<String, String> m = new HashMap<>(rows.get(0).values());
    m.put("sourceUpdatedAt", "2026-11-17T12:00:00.123456789");
    var input = List.of(new InputRow(2, m, null));
    imports.ingest(source, "micro", input);
    var repeated = imports.ingest(source, "micro", input);
    assertThat(((Number) repeated.get("duplicate_count")).intValue()).isEqualTo(1);
  }

  @Autowired RefreshScheduler scheduler;

  @org.springframework.beans.factory.annotation.Value("${scm.mock-port}")
  int mockPort;

  @Test
  void scheduledRefreshPersistsUnavailableState() {
    Source s = new Source();
    s.name = "scheduled-test-" + UUID.randomUUID();
    s.type = "HTTP";
    s.endpoint = "http://127.0.0.1:" + mockPort + "/api/failure";
    s.mappingConfig = json.createObjectNode();
    s.enabled = true;
    s = sources.save(s);
    scheduler.refresh();
    assertThat(sources.get(s.id).syncStatus).isEqualTo("FAILED");
  }

  @Test
  void legacyExcelAcceptsValues() throws Exception {
    try (var book = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = book.createSheet("tasks");
      var head = sheet.createRow(0);
      var row = sheet.createRow(1);
      int i = 0;
      for (var e : rows.get(0).values().entrySet()) {
        head.createCell(i).setCellValue(e.getKey());
        row.createCell(i++).setCellValue(e.getValue());
      }
      book.write(out);
      var input =
          files.read(
              new MockMultipartFile(
                  "file", "sample.xls", "application/octet-stream", out.toByteArray()));
      assertThat(
              ((Number) imports.ingest(source, "sample.xls", input).get("success_count"))
                  .intValue())
          .isEqualTo(1);
    }
  }

  @Test
  void configRejectsCoercionAndInvalidIcons() {
    var c = (com.fasterxml.jackson.databind.node.ObjectNode) configs.get().deepCopy();
    c.put("dueSoonHours", 24.5);
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("无效");
    c.put("dueSoonHours", 24);
    c.put("aiExpanded", "yes");
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("无效");
    c.put("aiExpanded", true);
    ((com.fasterxml.jackson.databind.node.ObjectNode) c.path("cards").get(0))
        .put("icon", "unsupported");
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("无效");
  }

  @Test
  void metricSourceCanChangeWithoutChangingCardIdentity() {
    var c = (com.fasterxml.jackson.databind.node.ObjectNode) configs.get().deepCopy();
    var card = (com.fasterxml.jackson.databind.node.ObjectNode) c.path("cards").get(0);
    String key = card.path("key").asText();
    card.put("metric", "OVERDUE");
    configs.save(c);
    assertThat(configs.get().path("cards").get(0).path("key").asText()).isEqualTo(key);
    assertThat(configs.get().path("cards").get(0).path("metric").asText()).isEqualTo("OVERDUE");
    card.put("metric", "NON_EXISTENT");
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("无效");
  }

  @Test
  void navigationCyclesAndInvalidPromptAreRejected() {
    var c = (com.fasterxml.jackson.databind.node.ObjectNode) configs.get().deepCopy();
    var nav = (com.fasterxml.jackson.databind.node.ObjectNode) c.path("navigation").get(0);
    nav.put("parent", nav.path("key").asText());
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("循环");
    nav.put("parent", "");
    ((com.fasterxml.jackson.databind.node.ObjectNode) c.path("shortcuts").get(0))
        .put("aiPrompt", 123);
    assertThatThrownBy(() -> configs.save(c)).hasMessageContaining("提示词");
  }
}
