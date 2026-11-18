package cn.cvte.scm;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.cvte.scm.integration.controller.SourcePreviewController;
import cn.cvte.scm.integration.service.SourcePreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

@WebMvcTest(SourcePreviewController.class)
class ApiTest {
  @Autowired MockMvc mvc;
  @MockitoBean SourcePreviewService service;

  @Test
  void rejectsInvalidType() throws Exception {
    mvc.perform(get("/api/data-sources/mock/preview?type=../secret"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(service);
  }

  @Test
  void reportsUnavailableSource() throws Exception {
    when(service.preview("procurement")).thenThrow(new RestClientException("unavailable"));
    mvc.perform(get("/api/data-sources/mock/preview"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("code").value("SOURCE_UNAVAILABLE"));
  }
}
