package cn.cvte.scm.integration.controller;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.integration.model.Source;
import cn.cvte.scm.integration.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-sources")
public class SourceController {
  private final SourceService sources;
  private final SyncService sync;

  public SourceController(SourceService s, SyncService y) {
    sources = s;
    sync = y;
  }

  @GetMapping
  public Object list() {
    return ApiResponse.ok(sources.list());
  }

  @PostMapping
  public Object create(@Valid @RequestBody Source s) {
    s.id = null;
    return ApiResponse.ok(sources.save(s));
  }

  @PutMapping("/{id}")
  public Object save(@PathVariable long id, @Valid @RequestBody Source s) {
    s.id = id;
    return ApiResponse.ok(sources.save(s));
  }

  @PostMapping("/{id}/sync")
  public Object sync(@PathVariable long id) {
    return ApiResponse.ok(sync.sync(id));
  }
}
