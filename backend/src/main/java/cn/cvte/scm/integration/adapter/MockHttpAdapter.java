package cn.cvte.scm.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MockHttpAdapter implements SourceAdapter {
  private final RestClient client;

  public MockHttpAdapter(@Value("${scm.mock-url}") String url) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  public JsonNode preview(String type) {
    return client.get().uri("/api/" + type).retrieve().body(JsonNode.class);
  }
}
