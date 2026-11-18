package cn.cvte.scm.todo.mapper;

import cn.cvte.scm.todo.model.Todo;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TodoMapper {
  @Select("SELECT COUNT(*) FROM scm_todo")
  long count();

  @Select(
      "SELECT t.*,s.name AS source_system FROM scm_todo t JOIN data_source s ON s.id=t.source_id"
          + " ORDER BY t.id DESC")
  List<Todo> all();

  @Select(
      "SELECT t.*,s.name AS source_system FROM scm_todo t JOIN data_source s ON s.id=t.source_id"
          + " WHERE t.id=#{id}")
  Todo get(long id);

  @Select("SELECT * FROM scm_todo WHERE source_id=#{source} AND source_record_id=#{key} FOR UPDATE")
  Todo existing(@Param("source") long source, @Param("key") String key);

  @Insert(
      "INSERT INTO scm_todo"
          + " (source_id,source_record_id,business_no,title,description,business_type,source_page,status,risk_level,priority,owner,business_org,business_url,extra_json,created_at,due_at,source_updated_at,completed_at)"
          + " VALUES"
          + " (#{sourceId},#{sourceRecordId},#{businessNo},#{title},#{description},#{businessType},#{sourcePage},#{status},#{riskLevel},#{priority},#{owner},#{businessOrg},#{businessUrl},#{extraJson},#{createdAt},#{dueAt},#{sourceUpdatedAt},#{completedAt})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(Todo todo);

  @Update(
      "UPDATE scm_todo SET"
          + " business_no=#{businessNo},title=#{title},description=#{description},business_type=#{businessType},source_page=#{sourcePage},status=#{status},risk_level=#{riskLevel},priority=#{priority},owner=#{owner},business_org=#{businessOrg},business_url=#{businessUrl},extra_json=#{extraJson},created_at=#{createdAt},due_at=#{dueAt},source_updated_at=#{sourceUpdatedAt},completed_at=#{completedAt}"
          + " WHERE id=#{id}")
  void update(Todo todo);
}
