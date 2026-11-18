package cn.cvte.scm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.cvte.scm.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AiChatServiceTest {
  private final CompanyModel company = mock(CompanyModel.class);
  private final ChatModel model = mock(ChatModel.class);
  private final AiTools tools = mock(AiTools.class);
  private final AiConfigChanges changes = mock(AiConfigChanges.class);
  private final AiAudit audit = mock(AiAudit.class);
  private final ObjectMapper json = new ObjectMapper();
  private final AiChatService service = new AiChatService(company, tools, changes, audit, json);

  AiChatServiceTest() {
    when(company.ready()).thenReturn(true);
    when(company.chat()).thenReturn(model);
  }

  @Test
  void noToolMeansNoFabricatedNumbersOrLinks() {
    when(model.chat(any(ChatRequest.class))).thenReturn(text("逾期 555 条。https://fake.example"));
    var reply = service.chat(null, "有哪些任务逾期");
    assertThat(reply.evidence()).isEmpty();
    assertThat(reply.answer()).doesNotContain("555", "fake.example");
    verifyNoInteractions(tools, changes);
  }

  @Test
  void modelCannotCallConfigurationWriter() {
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(call(AiTools.UPDATE, "{\"confirm\":true}"), text("已保存"));
    var reply = service.chat(null, "直接改配置");
    assertThat(reply.pending()).isNull();
    assertThat(reply.tools()).extracting(AiChatService.ToolRun::status).containsExactly("FAILED");
    assertThat(reply.answer()).doesNotContain("已保存");
    verifyNoInteractions(changes, tools);
    verify(audit).record(eq("unknown_tool"), isNull(), eq("FAILED"), anyLong(), anyString());
  }

  @Test
  void rejectsInvalidToolFieldsAndReturnsErrorToModel() {
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(call(AiTools.SEARCH, "{\"invented\":\"sensitive\"}"), text("完成"));
    var reply = service.chat(null, "查待办");
    assertThat(reply.tools().get(0).message()).isEqualTo("工具包含未知参数");
    var prompts = ArgumentCaptor.forClass(ChatRequest.class);
    verify(model, times(2)).chat(prompts.capture());
    assertThat(prompts.getValue().messages())
        .anySatisfy(
            message -> {
              assertThat(message).isInstanceOf(ToolExecutionResultMessage.class);
              assertThat(((ToolExecutionResultMessage) message).text()).contains("工具包含未知参数");
            });
    verifyNoInteractions(tools, changes);
    verify(audit).record(eq(AiTools.SEARCH), isNull(), eq("FAILED"), anyLong(), anyString());
  }

  @Test
  void confirmationIsBoundToTheSessionAndCanOnlyBeUsedOnce() {
    var proposal = prepare(Instant.now().plusSeconds(300));
    var first = service.chat(null, "移动逾期卡片");
    var other = service.chat(null, "移动逾期卡片");
    UUID token = first.pending().id();
    verify(changes, never()).apply(any(), anyLong());
    assertStatus(
        HttpStatus.NOT_FOUND, () -> service.decide(first.sessionId(), UUID.randomUUID(), true));
    assertStatus(HttpStatus.NOT_FOUND, () -> service.decide(other.sessionId(), token, true));
    assertStatus(HttpStatus.CONFLICT, () -> service.chat(first.sessionId(), "确认"));
    assertThat(service.decide(first.sessionId(), token, true).applied()).isTrue();
    assertStatus(HttpStatus.NOT_FOUND, () -> service.decide(first.sessionId(), token, true));
    verify(changes).apply(eq(proposal), anyLong());
  }

  @Test
  void expiredProposalCannotBeApplied() {
    prepare(Instant.now().minusSeconds(5));
    var reply = service.chat(null, "移动逾期卡片");
    assertStatus(
        HttpStatus.GONE, () -> service.decide(reply.sessionId(), reply.pending().id(), true));
    verify(changes, never()).apply(any(), anyLong());
    verify(audit).record(eq(AiTools.UPDATE), isNull(), eq("EXPIRED"), anyLong(), isNull());
  }

  @Test
  void unavailableAndProviderErrorsAreDistinctAndSanitized() {
    when(company.ready()).thenReturn(false);
    assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.chat(null, "查待办"));
    when(company.ready()).thenReturn(true);
    when(model.chat(any(ChatRequest.class)))
        .thenThrow(new RuntimeException("private provider body"));
    assertThatThrownBy(() -> service.chat(null, "查待办"))
        .isInstanceOf(BusinessException.class)
        .hasMessageNotContaining("private provider body")
        .satisfies(
            ex -> assertThat(((BusinessException) ex).status).isEqualTo(HttpStatus.BAD_GATEWAY));
    verify(audit).record(eq("model_request"), isNull(), eq("FAILED"), anyLong(), anyString());
  }

  private AiConfigChanges.Proposal prepare(Instant expiresAt) {
    var proposal =
        new AiConfigChanges.Proposal(
            new AiConfigChanges.Preview(
                UUID.randomUUID(),
                List.of(new AiConfigChanges.Change("已逾期", "位置", "3", "1")),
                expiresAt),
            json.createObjectNode(),
            json.createObjectNode());
    var other =
        new AiConfigChanges.Proposal(
            new AiConfigChanges.Preview(UUID.randomUUID(), proposal.preview().changes(), expiresAt),
            proposal.before(),
            proposal.after());
    when(changes.propose("OVERDUE", 1, null, null)).thenReturn(proposal, other);
    var request = call(AiTools.PROPOSE, "{\"card\":\"OVERDUE\",\"position\":1}");
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(request, text("等待确认"), request, text("等待确认"));
    return proposal;
  }

  private static ChatResponse call(String name, String arguments) {
    return ChatResponse.builder()
        .aiMessage(
            AiMessage.from(
                ToolExecutionRequest.builder()
                    .id("call_123")
                    .name(name)
                    .arguments(arguments)
                    .build()))
        .build();
  }

  private static ChatResponse text(String text) {
    return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
  }

  private static void assertStatus(HttpStatus status, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).status).isEqualTo(status));
  }
}
