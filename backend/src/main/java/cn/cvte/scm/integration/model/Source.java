package cn.cvte.scm.integration.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class Source {
  public Long id;

  @NotBlank
  @Size(max = 100)
  public String name;

  @Pattern(regexp = "HTTP|FILE")
  @NotNull
  public String type;

  public String endpoint;

  @Pattern(regexp = "GET|POST")
  @NotNull
  public String requestMethod = "GET";

  @Min(100)
  @Max(30000)
  public int timeoutMs = 3000;

  @Min(1)
  @Max(1440)
  public int refreshInterval = 30;

  public JsonNode mappingConfig;
  public boolean enabled;
  public String syncStatus, lastError;
  public LocalDateTime lastSyncAt;
}
