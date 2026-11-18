package cn.cvte.scm.integration.controller;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.integration.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
  private final ImportService imports;
  private final FileImportService uploads;

  public ImportController(ImportService imports, FileImportService uploads) {
    this.imports = imports;
    this.uploads = uploads;
  }

  @PostMapping
  public Object upload(@RequestParam long sourceId, @RequestPart MultipartFile file) {
    return ApiResponse.ok(uploads.upload(sourceId, file));
  }

  @GetMapping
  public Object list() {
    return ApiResponse.ok(imports.batches());
  }

  @GetMapping("/{id}")
  public Object batch(@PathVariable long id) {
    return ApiResponse.ok(imports.batch(id));
  }
}
