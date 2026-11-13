package co.edu.ufps.notsonaive.service;

import co.edu.ufps.notsonaive.algorithm.NotSoNaive;
import co.edu.ufps.notsonaive.algorithm.SearchStatistics;
import co.edu.ufps.notsonaive.exception.InvalidPatternException;
import co.edu.ufps.notsonaive.model.SearchResult;
import co.edu.ufps.notsonaive.model.TextDocument;
import org.springframework.stereotype.Service;
import ufps.util.colecciones_seed.ListaCD;
import ufps.util.colecciones_seed.Secuencia;

/**
 * Coordinates a search: validates the pattern, runs {@link NotSoNaive}
 * (the only matcher used in production) and packages the result.
 */
@Service
public class SearchService {

    /** Texts up to this length also get a step-by-step trace of the attempts. */
    public static final int TRACE_TEXT_LIMIT = 300;

    /** Maximum accepted pattern length. */
    public static final int MAX_PATTERN_LENGTH = 1000;

    /**
     * Validates a pattern typed by the user.
     * The pattern must be non-empty printable ASCII (0x20-0x7E); it is used
     * exactly as typed (no trimming) because the search is exact and case sensitive.
     */
    public String validatePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new InvalidPatternException("The pattern must not be empty. Type the text you want to find.");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new InvalidPatternException("The pattern is too long (maximum " + MAX_PATTERN_LENGTH
                    + " characters).");
        }
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new InvalidPatternException("The pattern may only contain printable ASCII characters; "
                        + "character " + (i + 1) + " (U+" + String.format("%04X", (int) c) + ") is not allowed.");
            }
        }
        return pattern;
    }

    /**
     * Runs Not So Naive over the document.
     *
     * @param document validated text
     * @param pattern  raw pattern (validated here)
     * @return result with SEED-backed positions and statistics
     */
    public SearchResult search(TextDocument document, String pattern) {
        String validPattern = validatePattern(pattern);
        String text = document.content();
        boolean trace = text.length() <= TRACE_TEXT_LIMIT;

        long start = System.nanoTime();
        NotSoNaive matcher = new NotSoNaive(validPattern);           // preprocessing, O(1)
        SearchStatistics stats = trace ? matcher.searchWithTrace(text) // searching
                : matcher.search(text);
        long elapsed = System.nanoTime() - start;

        return new SearchResult(document, validPattern, toSecuencia(stats.getPositions()), stats, elapsed);
    }

    /** Copies the list of occurrences into a SEED Secuencia of exact capacity. */
    static Secuencia<Integer> toSecuencia(ListaCD<Integer> list) {
        Secuencia<Integer> seq = new Secuencia<>(Math.max(1, list.getTamanio()));
        for (Integer p : list) {
            seq.insertar(p);
        }
        return seq;
    }
}
