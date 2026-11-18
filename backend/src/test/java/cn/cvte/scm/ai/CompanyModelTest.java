package cn.cvte.scm.ai;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SCM_AI_LIVE_TEST", matches = "true")
class CompanyModelTest {
  @Test
  void completesRealDialogueAndToolRoundTrip() throws Exception {
    var model =
        CompanyModel.create(
            System.getenv("SCM_AI_BASE_URL"), System.getenv("SCM_AI_API_KEY"), "gpt-6.1-sol");
    var text =
        model.chat(
            ChatRequest.builder()
                .messages(UserMessage.from("Reply with exactly SCM_CHAT_OK."))
                .build());
    assertEquals("SCM_CHAT_OK", text.aiMessage().text().strip());
    System.out.println(
        "Company chat: " + text.metadata().modelName() + " " + text.aiMessage().text());

    var tool =
        ToolSpecification.builder()
            .name("read_probe_value")
            .description("Read the backend verification value")
            .parameters(JsonObjectSchema.builder().addStringProperty("key").required("key").build())
            .build();
    var question =
        UserMessage.from(
            "Call read_probe_value with key scm. Reply with the exact value from its result.");
    var response =
        model.chat(
            ChatRequest.builder()
                .messages(question)
                .toolSpecifications(tool)
                .toolChoice(ToolChoice.REQUIRED)
                .build());
    assertEquals(1, response.aiMessage().toolExecutionRequests().size());
    var call = response.aiMessage().toolExecutionRequests().get(0);
    assertEquals("read_probe_value", call.name());
    assertEquals(
        "scm",
        new com.fasterxml.jackson.databind.ObjectMapper()
            .reader()
            .readTree(call.arguments())
            .path("key")
            .asText());
    String value = "SCM_TOOL_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    var answer =
        model.chat(
            ChatRequest.builder()
                .messages(
                    question, response.aiMessage(), ToolExecutionResultMessage.from(call, value))
                .toolSpecifications(tool)
                .build());
    assertTrue(answer.aiMessage().text().contains(value));
    System.out.println(
        "Company tool round trip: "
            + response.metadata().modelName()
            + " "
            + call.name()
            + " "
            + call.arguments()
            + " -> "
            + answer.aiMessage().text());
  }
}
