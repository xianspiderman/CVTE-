package cn.cvte.scm;

import static org.assertj.core.api.Assertions.assertThat;

import cn.cvte.scm.todo.mapper.TodoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SCM_DB_TEST", matches = "true")
class DatabaseIntegrationTest {
  @Autowired TodoMapper mapper;

  @Test
  void readsActualMysql() {
    assertThat(mapper.count()).isGreaterThanOrEqualTo(0);
  }
}
