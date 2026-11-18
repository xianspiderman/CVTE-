package cn.cvte.scm.ai;

import cn.cvte.scm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final AiChatService chat;
  private final AiAudit audit;

  AiController(AiChatService chat, AiAudit audit) {
    this.chat = chat;
    this.audit = audit;
  }

  record Message(UUID sessionId, @NotBlank @Size(max = 2000) String message) {}

  record Confirmation(
      @NotNull UUID sessionId, @NotNull UUID proposalId, @NotNull Boolean confirm) {}

  @GetMapping("/status")
  public ApiResponse<Map<String, Object>> status() {
    return ApiResponse.ok(Map.of("configured", chat.ready(), "model", chat.modelName()));
  }

  @PostMapping("/chat")
  public ApiResponse<AiChatService.Reply> chat(@Valid @RequestBody Message message) {
    return ApiResponse.ok(chat.chat(message.sessionId(), message.message()));
  }

  @PostMapping("/config/decision")
  public ApiResponse<AiChatService.Decision> decide(@Valid @RequestBody Confirmation confirmation) {
    return ApiResponse.ok(
        chat.decide(confirmation.sessionId(), confirmation.proposalId(), confirmation.confirm()));
  }

  @GetMapping("/logs")
  public ApiResponse<java.util.List<Map<String, Object>>> logs() {
    return ApiResponse.ok(audit.list());
  }
}
