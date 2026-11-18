package cn.cvte.scm.metric;

import cn.cvte.scm.common.ApiResponse;
import cn.cvte.scm.todo.model.TodoFilter;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
public class MetricController {
  private final MetricService service;

  public MetricController(MetricService s) {
    service = s;
  }

  @GetMapping("/summary")
  public Object summary(@ModelAttribute TodoFilter f) {
    f.validate();
    return ApiResponse.ok(service.summary(f));
  }

  @GetMapping("/charts")
  public Object charts(
      @ModelAttribute TodoFilter f, @RequestParam(defaultValue = "7") int trendDays) {
    return ApiResponse.ok(service.charts(f, trendDays));
  }

  @GetMapping("/overdue-trend")
  public Object overdueTrend(
      @ModelAttribute TodoFilter f, @RequestParam(defaultValue = "7") int trendDays) {
    return ApiResponse.ok(service.overdueTrend(f, trendDays));
  }

  @InitBinder
  void fields(org.springframework.web.bind.WebDataBinder binder) {
    binder.initDirectFieldAccess();
  }
}
