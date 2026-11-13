package co.edu.ufps.notsonaive.exception;

/** Raised when the search pattern is missing or not acceptable. */
public class InvalidPatternException extends RuntimeException {
    public InvalidPatternException(String message) {
        super(message);
    }
}
