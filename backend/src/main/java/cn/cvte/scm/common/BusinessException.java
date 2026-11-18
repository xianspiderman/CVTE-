package cn.cvte.scm.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  public final HttpStatus status;

  public BusinessException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static BusinessException bad(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }

  public static BusinessException missing() {
    return new BusinessException(HttpStatus.NOT_FOUND, "记录不存在");
  }
}
