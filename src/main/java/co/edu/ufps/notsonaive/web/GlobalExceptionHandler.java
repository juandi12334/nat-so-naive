package co.edu.ufps.notsonaive.web;

import co.edu.ufps.notsonaive.exception.InvalidFileException;
import co.edu.ufps.notsonaive.exception.InvalidPatternException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;


/** Converts errors into JSON messages the interface can show to the user. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** JSON body for errors: {@code {"error": "..."}}. */
    public record ErrorResponse(String error) {
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InvalidFileException.class, InvalidPatternException.class})
    public ResponseEntity<ErrorResponse> invalidInput(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The file exceeds the maximum allowed size.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> multipart(MultipartException e) {
        return error(HttpStatus.BAD_REQUEST, "The request must include a .txt file and a pattern.");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(Exception e) {
        return error(HttpStatus.NOT_FOUND, "Not found.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected processing error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected processing error. Please try again.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(message));
    }
}
