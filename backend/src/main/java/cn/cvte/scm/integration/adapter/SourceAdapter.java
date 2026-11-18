package cn.cvte.scm.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;

public interface SourceAdapter {
  JsonNode preview(String businessType);
}
