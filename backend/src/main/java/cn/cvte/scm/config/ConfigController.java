package cn.cvte.scm.config;

import cn.cvte.scm.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench/config")
public class ConfigController {
  private final ConfigService service;

  public ConfigController(ConfigService s) {
    service = s;
  }

  @GetMapping
  public Object get() {
    return ApiResponse.ok(service.get());
  }

  @PutMapping
  public Object save(@RequestBody JsonNode config) {
    return ApiResponse.ok(service.save(config));
  }
}
