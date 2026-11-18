package cn.cvte.scm.ai;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CompanyModel {
  private final String name;
  private final ChatModel chat;

  public CompanyModel(
      @Value("${scm.ai.base-url:}") String url,
      @Value("${scm.ai.api-key:}") String key,
      @Value("${scm.ai.model:gpt-6.1-sol}") String name) {
    this.name = name;
    this.chat = url.isBlank() || key.isBlank() ? null : create(url, key, name);
  }

  public boolean ready() {
    return chat != null;
  }

  String name() {
    return name;
  }

  ChatModel chat() {
    return chat;
  }

  static ChatModel create(String url, String key, String name) {
    URI target = URI.create(url);
    if (!"https".equals(target.getScheme())
        || target.getHost() == null
        || target.getUserInfo() != null
        || target.getRawQuery() != null
        || target.getFragment() != null) {
      throw new IllegalArgumentException("SCM_AI_BASE_URL 必须是有效的 HTTPS API 基址");
    }
    // The internal gateway is reached over the corporate network, outside OS proxy routing.
    var transport =
        HttpClient.newBuilder()
            .proxy(ProxySelector.of(null))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1);
    return OpenAiResponsesChatModel.builder()
        .apiKey(key)
        .baseUrl(url)
        .modelName(name)
        .httpClientBuilder(
            new JdkHttpClientBuilder()
                .httpClientBuilder(transport)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(25)))
        .store(false)
        .parallelToolCalls(false)
        .reasoningEffort("low")
        .maxOutputTokens(1800)
        .build();
  }
}
