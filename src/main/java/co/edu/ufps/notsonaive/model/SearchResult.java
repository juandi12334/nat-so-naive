package co.edu.ufps.notsonaive.model;

import co.edu.ufps.notsonaive.algorithm.SearchStatistics;
import ufps.util.colecciones_seed.Secuencia;

/**
 * Outcome of searching a pattern in a document with Not So Naive.
 *
 * <p>The occurrences are stored in a SEED {@link Secuencia} sized exactly to
 * the number of matches, which gives O(1) indexed access for paging the
 * positions in the UI and for walking them while laying out the PDF.</p>
 */
public final class SearchResult {

    private final TextDocument document;
    private final String pattern;
    private final Secuencia<Integer> positions;
    private final SearchStatistics statistics;
    private final long elapsedNanos;

    public SearchResult(TextDocument document, String pattern, Secuencia<Integer> positions,
                        SearchStatistics statistics, long elapsedNanos) {
        this.document = document;
        this.pattern = pattern;
        this.positions = positions;
        this.statistics = statistics;
        this.elapsedNanos = elapsedNanos;
    }

    public TextDocument getDocument() {
        return document;
    }

    public String getPattern() {
        return pattern;
    }

    /** @return 0-based start positions, in increasing order (SEED Secuencia) */
    public Secuencia<Integer> getPositions() {
        return positions;
    }

    public int getMatchCount() {
        return positions.getTamanio();
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }

    /** @return wall-clock time of preprocessing + searching, in nanoseconds */
    public long getElapsedNanos() {
        return elapsedNanos;
    }
}
