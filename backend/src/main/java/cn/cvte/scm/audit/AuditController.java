package cn.cvte.scm.audit;

import cn.cvte.scm.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
public class AuditController {
  private final AuditStore store;

  public AuditController(AuditStore s) {
    store = s;
  }

  @GetMapping
  public Object list() {
    return ApiResponse.ok(store.list());
  }
}
