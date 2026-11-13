package co.edu.ufps.notsonaive.exception;

/** Raised when the uploaded file is not a valid, non-empty ASCII .txt file. */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
