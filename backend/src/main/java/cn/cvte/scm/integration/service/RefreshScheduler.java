package cn.cvte.scm.integration.service;

import cn.cvte.scm.integration.store.SourceStore;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class RefreshScheduler {
  private final SourceStore sources;
  private final SyncService sync;

  public RefreshScheduler(SourceStore s, SyncService y) {
    sources = s;
    sync = y;
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void refresh() {
    for (var s : sources.list())
      if (s.enabled
          && "HTTP".equals(s.type)
          && (s.lastSyncAt == null
              || s.lastSyncAt
                  .plusMinutes(s.refreshInterval)
                  .isBefore(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")))))
        try {
          sync.sync(s.id);
        } catch (Exception ignored) {
          /* SyncService persists sanitized failure state. */
        }
  }
}
