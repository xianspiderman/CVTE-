package cn.cvte.scm.integration.adapter;

import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.service.SourceService;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredHttpAdapter {
  private final SourceService sources;
  private final ObjectMapper json;

  public ConfiguredHttpAdapter(SourceService s, ObjectMapper j) {
    sources = s;
    json = j;
  }

  public List<InputRow> fetch(Source s) {
    sources.validEndpoint(s.endpoint);
    HttpURLConnection c = null;
    try {
      c = (HttpURLConnection) URI.create(s.endpoint).toURL().openConnection();
      c.setInstanceFollowRedirects(false);
      c.setRequestMethod(s.requestMethod);
      c.setConnectTimeout(s.timeoutMs);
      c.setReadTimeout(s.timeoutMs);
      c.setRequestProperty("Accept", "application/json");
      if (c.getResponseCode() != 200) throw new Exception();
      byte[] bytes;
      try (var in = c.getInputStream()) {
        bytes = in.readNBytes(5 * 1024 * 1024 + 1);
      }
      if (bytes.length > 5 * 1024 * 1024) throw new Exception();
      JsonNode root = json.readTree(bytes);
      JsonNode rows = root.isArray() ? root : root.path("data");
      if (!rows.isArray() || rows.size() > 10000) throw new Exception();
      List<InputRow> result = new ArrayList<>();
      int line = 0;
      for (JsonNode n : rows) {
        line++;
        Map<String, String> values = new LinkedHashMap<>();
        if (!n.isObject()) {
          result.add(new InputRow(line, values, "必须是 JSON 对象"));
          continue;
        }
        n.fields()
            .forEachRemaining(
                e ->
                    values.put(
                        e.getKey(),
                        e.getValue().isContainerNode()
                            ? e.getValue().toString()
                            : e.getValue().asText("")));
        result.add(new InputRow(line, values, null));
      }
      return result;
    } catch (Exception e) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "数据源请求失败：请检查 Mock 状态、HTTP 状态码、超时或 JSON 数组格式");
    } finally {
      if (c != null) c.disconnect();
    }
  }
}
