package cn.cvte.scm.common;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class BusinessTime {
  @Bean
  public Clock businessClock(@Value("${scm.fixed-now:}") String fixed) {
    return fixed.isBlank()
        ? Clock.system(ZoneId.of("Asia/Shanghai"))
        : Clock.fixed(Instant.parse(fixed), ZoneId.of("Asia/Shanghai"));
  }
}
