package co.edu.ufps.notsonaive.service;

import ufps.util.colecciones_seed.Secuencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout logic for the PDF, separated from drawing so it can be tested.
 *
 * <p>The text is laid out on a monospaced grid of {@code columns} characters
 * per visual line. Every character keeps a link to its original 0-based index,
 * so each occurrence {@code [p, p+m)} returned by Not So Naive is converted
 * into one highlight segment per visual line it touches. Rules:</p>
 * <ul>
 *   <li>LF ends a logical line (it is not drawn).</li>
 *   <li>TAB expands to the next multiple of {@link #TAB_SIZE} columns.</li>
 *   <li>Lines longer than {@code columns} wrap; an occurrence crossing a wrap
 *       produces two (or more) segments, all tied to the same match index.</li>
 *   <li>Patterns cannot contain LF (they are single-line printable ASCII),
 *       so an occurrence never crosses a logical line break.</li>
 * </ul>
 */
public final class PdfLayoutEngine {

    public static final int TAB_SIZE = 4;

    /**
     * A highlighted run on one visual line.
     *
     * @param matchIndex index of the occurrence in the positions sequence
     * @param position   0-based text position of that occurrence
     * @param line       visual line index (0-based, across all pages)
     * @param startCol   first highlighted column (inclusive)
     * @param endCol     last highlighted column (exclusive)
     */
    public record Segment(int matchIndex, int position, int line, int startCol, int endCol) {
    }

    /**
     * @param lines        visual lines (tabs already expanded)
     * @param logicalLine  for each visual line, its 1-based source line number,
     *                     or 0 when it is the continuation of a wrapped line
     * @param segments     highlight segments, ordered by match then line
     */
    public record Layout(List<String> lines, int[] logicalLine, List<Segment> segments) {
    }

    private final int columns;

    public PdfLayoutEngine(int columns) {
        if (columns < TAB_SIZE) {
            throw new IllegalArgumentException("columns must be >= " + TAB_SIZE);
        }
        this.columns = columns;
    }

    public int getColumns() {
        return columns;
    }

    public Layout layout(String text, Secuencia<Integer> positions, int patternLength) {
        final int n = text.length();
        final int[] lineOf = new int[n];
        final int[] colOf = new int[n];
        final List<String> lines = new ArrayList<>();
        final List<Integer> logical = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        int col = 0;
        int logicalNumber = 1;
        boolean continuation = false;

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineOf[i] = lines.size();
                colOf[i] = col;
                lines.add(current.toString());
                logical.add(continuation ? 0 : logicalNumber);
                current = new StringBuilder();
                col = 0;
                logicalNumber++;
                continuation = false;
                continue;
            }
            int width = c == '\t' ? TAB_SIZE - (col % TAB_SIZE) : 1;
            if (col + width > columns) { // wrap
                lines.add(current.toString());
                logical.add(continuation ? 0 : logicalNumber);
                current = new StringBuilder();
                col = 0;
                continuation = true;
                width = c == '\t' ? TAB_SIZE : 1;
            }
            lineOf[i] = lines.size();
            colOf[i] = col;
            if (c == '\t') {
                current.append(" ".repeat(width));
            } else {
                current.append(c);
            }
            col += width;
        }
        if (current.length() > 0 || n == 0 || continuation) {
            lines.add(current.toString());
            logical.add(continuation ? 0 : logicalNumber);
        }

        List<Segment> segments = new ArrayList<>();
        for (int t = 0; t < positions.getTamanio(); t++) {
            int p = positions.get(t);
            int end = p + patternLength; // exclusive
            int segLine = lineOf[p];
            int segStart = colOf[p];
            int segEnd = colOf[p] + 1;
            for (int i = p + 1; i < end; i++) {
                if (lineOf[i] != segLine) {
                    segments.add(new Segment(t, p, segLine, segStart, segEnd));
                    segLine = lineOf[i];
                    segStart = colOf[i];
                }
                segEnd = colOf[i] + 1;
            }
            segments.add(new Segment(t, p, segLine, segStart, segEnd));
        }

        int[] logicalArr = logical.stream().mapToInt(Integer::intValue).toArray();
        return new Layout(lines, logicalArr, segments);
    }
}
