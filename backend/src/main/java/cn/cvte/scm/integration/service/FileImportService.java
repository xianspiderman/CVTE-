package cn.cvte.scm.integration.service;

import cn.cvte.scm.audit.AuditStore;
import cn.cvte.scm.common.BusinessException;
import cn.cvte.scm.integration.adapter.FileAdapter;
import cn.cvte.scm.integration.store.SourceStore;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileImportService {
  private final SourceStore sources;
  private final ImportService imports;
  private final FileAdapter files;
  private final AuditStore audit;

  public FileImportService(
      SourceStore sources, ImportService imports, FileAdapter files, AuditStore audit) {
    this.sources = sources;
    this.imports = imports;
    this.files = files;
    this.audit = audit;
  }

  public Map<String, Object> upload(long sourceId, MultipartFile file) {
    var source = sources.get(sourceId);
    if (!"FILE".equals(source.type)) throw BusinessException.bad("仅支持向文件数据源导入");
    try {
      String name =
          java.nio.file.Path.of(
                  java.util.Optional.ofNullable(file.getOriginalFilename()).orElse("upload"))
              .getFileName()
              .toString();
      if (name.length() > 255) throw BusinessException.bad("文件名过长");
      return imports.ingest(source, name, files.read(file));
    } catch (Exception e) {
      sources.status(sourceId, "FAILED", "文件导入失败：请检查文件格式、大小或数据库状态");
      audit.record("FILE_IMPORT", Long.toString(sourceId), "FAILED");
      throw e;
    }
  }
}
