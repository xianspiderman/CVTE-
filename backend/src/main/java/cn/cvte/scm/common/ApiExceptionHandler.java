package cn.cvte.scm.common;

import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    IllegalArgumentException.class,
    org.springframework.validation.BindException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiResponse<Void>> invalid(Exception ex) {
    return ResponseEntity.badRequest().body(new ApiResponse<>("INVALID_ARGUMENT", "请求参数不合法", null));
  }

  @ExceptionHandler(RestClientException.class)
  ResponseEntity<ApiResponse<Void>> upstream(Exception ex) {
    return ResponseEntity.status(502)
        .body(new ApiResponse<>("SOURCE_UNAVAILABLE", "Mock 数据源不可用，请检查服务日志", null));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
    return ResponseEntity.status(ex.status)
        .body(new ApiResponse<>(ex.status.name(), ex.getMessage(), null));
  }

  @ExceptionHandler({org.springframework.web.multipart.MaxUploadSizeExceededException.class})
  ResponseEntity<ApiResponse<Void>> large(Exception ex) {
    return ResponseEntity.status(413).body(new ApiResponse<>("FILE_TOO_LARGE", "文件不能超过 5MB", null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
    LoggerFactory.getLogger(getClass()).error("Request failed: {}", ex.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(new ApiResponse<>("INTERNAL_ERROR", "服务异常，请检查后端日志", null));
  }
}
