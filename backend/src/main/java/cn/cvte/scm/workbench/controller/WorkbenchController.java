package cn.cvte.scm.workbench.controller;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.workbench.service.WorkbenchStatusService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {
  private final WorkbenchStatusService service;

  public WorkbenchController(WorkbenchStatusService service) {
    this.service = service;
  }

  @GetMapping("/status")
  public ApiResponse<Map<String, Object>> status() {
    return ApiResponse.ok(service.status());
  }
}
