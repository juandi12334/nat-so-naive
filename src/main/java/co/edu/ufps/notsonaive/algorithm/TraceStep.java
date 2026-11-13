package co.edu.ufps.notsonaive.algorithm;

/**
 * One attempt of the Not So Naive search: the window starting at text
 * position {@code j}, what happened, and how far the window moved.
 *
 * @param j           0-based start of the window in the text
 * @param outcome     result of the attempt
 * @param shift       positions the window moved afterwards (k or ell)
 * @param comparisons character comparisons performed in this attempt
 */
public record TraceStep(int j, Outcome outcome, int shift, long comparisons) {

    /** Result of a single attempt. */
    public enum Outcome {
        /** x[1] != y[j+1]: window shifts k. */
        MISMATCH_SECOND,
        /** x[1] matched but some x[i], 2 &lt;= i &lt; m, did not: window shifts ell. */
        MISMATCH_REST,
        /** x[1..m-1] matched but x[0] != y[j]: window shifts ell. */
        MISMATCH_FIRST,
        /** Full occurrence reported at j. */
        MATCH
    }
}
