package cn.cvte.scm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.integration.adapter.ConfiguredHttpAdapter;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.service.SourceService;
import cn.cvte.scm.integration.store.SourceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.*;

class HttpAdapterTest {
  HttpServer server;
  ConfiguredHttpAdapter adapter;
  Source source;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/good",
        e -> {
          byte[] body = "{\"data\":[{\"id\":\"demo\"}]}".getBytes();
          e.sendResponseHeaders(200, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.createContext(
        "/api/bad",
        e -> {
          byte[] body = "{broken".getBytes();
          e.sendResponseHeaders(200, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.createContext(
        "/api/redirect",
        e -> {
          e.getResponseHeaders()
              .add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/good");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    server.createContext(
        "/api/slow",
        e -> {
          try {
            Thread.sleep(250);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          e.close();
        });
    server.start();
    adapter =
        new ConfiguredHttpAdapter(
            new SourceService(
                mock(SourceStore.class), mock(AuditStore.class), server.getAddress().getPort()),
            new ObjectMapper());
    source = new Source();
    source.requestMethod = "POST";
    source.timeoutMs = 1000;
  }

  void path(String path) {
    source.endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/" + path;
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void acceptsJsonPost() {
    path("good");
    assertThat(adapter.fetch(source)).hasSize(1);
  }

  @Test
  void malformedJsonIsExplicitFailure() {
    path("bad");
    assertThatThrownBy(() -> adapter.fetch(source)).hasMessageContaining("数据源请求失败");
  }

  @Test
  void redirectsAreNotFollowed() {
    path("redirect");
    assertThatThrownBy(() -> adapter.fetch(source)).hasMessageContaining("数据源请求失败");
  }

  @Test
  void readTimeoutIsBounded() {
    source.timeoutMs = 100;
    path("slow");
    assertThatThrownBy(() -> adapter.fetch(source)).hasMessageContaining("超时");
  }
}
