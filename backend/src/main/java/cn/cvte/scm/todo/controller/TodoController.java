package cn.cvte.scm.todo.controller;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.todo.model.TodoFilter;
import cn.cvte.scm.todo.service.TodoReadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
  private final TodoReadService service;

  public TodoController(TodoReadService s) {
    service = s;
  }

  @GetMapping
  public Object list(@ModelAttribute TodoFilter f) {
    return ApiResponse.ok(service.page(f));
  }

  @GetMapping("/{id}")
  public Object get(@PathVariable long id) {
    return ApiResponse.ok(service.get(id));
  }

  @InitBinder
  void fields(org.springframework.web.bind.WebDataBinder binder) {
    binder.initDirectFieldAccess();
  }
}
