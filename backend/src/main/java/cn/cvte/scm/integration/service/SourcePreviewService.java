package cn.cvte.scm.integration.service;

import cn.cvte.scm.integration.adapter.SourceAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class SourcePreviewService {
  private final SourceAdapter adapter;

  public SourcePreviewService(SourceAdapter adapter) {
    this.adapter = adapter;
  }

  public JsonNode preview(String type) {
    return adapter.preview(type);
  }
}
