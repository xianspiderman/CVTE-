package cn.cvte.scm.integration.controller;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.integration.service.SourcePreviewService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-sources")
public class SourcePreviewController {
  private final SourcePreviewService service;

  public SourcePreviewController(SourcePreviewService service) {
    this.service = service;
  }

  @GetMapping("/mock/preview")
  public ApiResponse<JsonNode> preview(
      @RequestParam(defaultValue = "procurement")
          @Pattern(regexp = "procurement|inventory|suppliers")
          String type) {
    return ApiResponse.ok(service.preview(type));
  }
}
