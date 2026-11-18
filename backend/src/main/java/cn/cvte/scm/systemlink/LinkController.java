package cn.cvte.scm.systemlink;

import cn.cvte.scm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system-links")
public class LinkController {
  public record Template(
      @Positive long sourceId, @NotBlank String pageCode, @NotBlank String urlTemplate) {}

  private final LinkService service;

  public LinkController(LinkService s) {
    service = s;
  }

  @GetMapping
  public Object list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping
  public Object save(@Valid @RequestBody Template t) {
    service.save(t.sourceId(), t.pageCode(), t.urlTemplate());
    return ApiResponse.ok("saved");
  }

  @GetMapping("/resolve")
  public Object resolve(
      @RequestParam long taskId, @RequestParam(required = false) String pageCode) {
    return ApiResponse.ok(service.resolve(taskId, pageCode));
  }
}
