package kr.ac.koreatech.indoor.vps.api;

import kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.ClientApiErrorResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    @ExceptionHandler(ClientApiException.class)
    ResponseEntity<ClientApiErrorResponse> handleClientApi(ClientApiException exception) {
        return ResponseEntity.status(exception.statusCode())
                .body(new ClientApiErrorResponse(
                        exception.code(),
                        exception.getMessage(),
                        exception.detail()
                ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class
    })
    ResponseEntity<ClientApiErrorResponse> handleValidation(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ClientApiErrorResponse(
                        "VALIDATION_ERROR",
                        "request validation failed",
                        errorDetail(exception)
                ));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            TypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ClientApiErrorResponse> handleRequestBinding(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ClientApiErrorResponse(
                        "VALIDATION_ERROR",
                        "request validation failed",
                        errorDetail(exception)
                ));
    }

    private Map<String, Object> errorDetail(Exception exception) {
        return Map.of("errors", List.of(Map.of("message", exception.getMessage())));
    }
}
