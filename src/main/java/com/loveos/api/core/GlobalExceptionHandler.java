package com.loveos.api.core;

import java.util.List;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * THE ONE PLACE AN ERROR BECOMES A RESPONSE.
 *
 * <p>Without this, each controller invents its own failure shape and the client's
 * single unwrapping path breaks. With it, a handler can throw an
 * {@link AppException} and know the body, the status and the request id are all
 * handled correctly.
 *
 * <p>THE LAST HANDLER IS THE SECURITY-CRITICAL ONE. An unanticipated exception
 * returns a bare {@code INTERNAL_ERROR} with a fixed message, while the real
 * cause goes to the log. Spring's default would surface the exception message —
 * which for a JDBC failure means table names, SQL fragments and sometimes data.
 * That is OWASP A09 territory, and it is the difference between an attacker
 * learning nothing and learning the schema.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** A rule the application enforced on purpose. Expected, so logged quietly. */
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorResponse> handleApp(AppException exception) {
    log.debug("rejected: {} {}", exception.code(), exception.getMessage());
    return ResponseEntity.status(exception.code().status())
        .body(ErrorResponse.of(exception.code(), exception.getMessage()));
  }

  /**
   * Bean-validation failures on a {@code @Valid} body.
   *
   * <p>422 rather than 400: the body was well-formed JSON and merely failed the
   * rules. The client distinguishes the two — {@code INVALID_REQUEST} means
   * "unparseable", {@code VALIDATION_ERROR} means "fix these fields" — and can
   * only show per-field messages for the latter.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    List<ErrorResponse.FieldIssue> issues =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ErrorResponse.FieldIssue(
                error.getField(),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
            .toList();

    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
        .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Request failed validation", issues));
  }

  /** Malformed JSON, a wrong content type, or a body that could not be bound. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
    // The parser's message quotes the offending input; it is not echoed back.
    log.debug("unreadable body: {}", exception.getMessage());
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
        .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "Request body could not be read"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadTooLarge(
      MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
        .body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, "Files must be under 25 MB"));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingPart(
      MissingServletRequestPartException exception) {
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
        .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "No file uploaded"));
  }

  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ErrorResponse> handleParameterValidation(Exception exception) {
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
        .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Request parameters failed validation"));
  }

  /** An unmatched path, so a 404 is an API error like any other, not an HTML page. */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(ErrorCode.NOT_FOUND, "No such endpoint"));
  }

  /** Everything unforeseen. Logged in full, disclosed not at all. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    log.error("unhandled exception", exception);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
        .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Something went wrong"));
  }
}
