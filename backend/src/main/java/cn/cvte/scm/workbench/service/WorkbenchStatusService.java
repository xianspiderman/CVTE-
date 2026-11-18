package cn.cvte.scm.workbench.service;

import cn.cvte.scm.ai.CompanyModel;
import cn.cvte.scm.todo.service.TodoReadService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchStatusService {
  private final TodoReadService todos;

  private final CompanyModel model;

  public WorkbenchStatusService(TodoReadService todos, CompanyModel model) {
    this.todos = todos;
    this.model = model;
  }

  public Map<String, Object> status() {
    return Map.of(
        "database",
        "UP",
        "storedTodoCount",
        todos.storedCount(),
        "phase",
        model.ready() ? "AI" : "BUSINESS",
        "syncReady",
        true,
        "metricsReady",
        true,
        "aiReady",
        model.ready());
  }
}
