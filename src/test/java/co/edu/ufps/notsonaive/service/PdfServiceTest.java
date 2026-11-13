package co.edu.ufps.notsonaive.service;

import co.edu.ufps.notsonaive.model.SearchResult;
import co.edu.ufps.notsonaive.model.TextDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import ufps.util.colecciones_seed.Secuencia;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PdfServiceTest {

    private final FileService fileService = new FileService(2 * 1024 * 1024);
    private final SearchService searchService = new SearchService();
    private final PdfService pdfService = new PdfService();

    private SearchResult search(String text, String pattern) {
        TextDocument doc = fileService.parse("t.txt", text.getBytes());
        return searchService.search(doc, pattern);
    }

    /**
     * Rebuilds, from the layout grid, the text under each highlight segment and checks it
     * equals the pattern, and that every position returned by Not So Naive is covered.
     */
    private void assertHighlightsMatchPositions(SearchResult r, PdfLayoutEngine.Layout layout) {
        Secuencia<Integer> positions = r.getPositions();
        String pattern = r.getPattern();
        Set<Integer> covered = new HashSet<>();
        StringBuilder[] rebuilt = new StringBuilder[positions.getTamanio()];
        for (PdfLayoutEngine.Segment s : layout.segments()) {
            assertEquals(positions.get(s.matchIndex()), s.position());
            String line = layout.lines().get(s.line());
            if (rebuilt[s.matchIndex()] == null) {
                rebuilt[s.matchIndex()] = new StringBuilder();
            }
            rebuilt[s.matchIndex()].append(line, s.startCol(), s.endCol());
            covered.add(s.matchIndex());
        }
        assertEquals(positions.getTamanio(), covered.size(), "every occurrence must be highlighted");
        for (int i = 0; i < rebuilt.length; i++) {
            assertEquals(pattern, rebuilt[i].toString(), "highlight #" + i + " must cover exactly the pattern");
        }
    }

    @Test
    void layoutHighlightsExactlyTheOccurrencesIncludingOverlaps() {
        SearchResult r = search("AAAAAA\nBANANA\nxAAx", "AA");
        PdfLayoutEngine.Layout layout = pdfService.layout(r);
        assertEquals(6, r.getMatchCount()); // 5 overlapping in "AAAAAA" + 1 in "xAAx"
        assertHighlightsMatchPositions(r, layout);
    }

    @Test
    void matchesAtStartAndEndOfLines() {
        SearchResult r = search("abXab\nab\nzzab", "ab");
        PdfLayoutEngine.Layout layout = pdfService.layout(r);
        assertArrayEquals(new int[]{0, 3, 6, 11}, r.getStatistics().positionsArray());
        assertHighlightsMatchPositions(r, layout);
        assertEquals(0, layout.segments().get(0).startCol());
        assertEquals(1, layout.segments().get(2).line());
        assertEquals(2, layout.segments().get(3).line());
        assertEquals(2, layout.segments().get(3).startCol());
    }

    @Test
    void occurrenceCrossingAVisualWrapIsSplitInTwoSegments() {
        int cols = PdfService.COLUMNS;
        String text = "x".repeat(cols - 2) + "HELLO" + "y";
        SearchResult r = search(text, "HELLO");
        PdfLayoutEngine.Layout layout = pdfService.layout(r);
        assertEquals(1, r.getMatchCount());
        List<PdfLayoutEngine.Segment> segs = layout.segments();
        assertEquals(2, segs.size());
        assertEquals(0, segs.get(0).line());
        assertEquals(1, segs.get(1).line());
        assertEquals(0, layout.logicalLine()[1]); // continuation line
        assertHighlightsMatchPositions(r, layout);
    }

    @Test
    void tabsAreExpandedWithoutBreakingHighlights() {
        SearchResult r = search("\tab\tab\n  \tab", "ab");
        PdfLayoutEngine.Layout layout = pdfService.layout(r);
        assertEquals(3, r.getMatchCount());
        assertHighlightsMatchPositions(r, layout);
    }

    @Test
    void generatesRealMultiPagePdfWithTheFullText() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            sb.append("Line ").append(i).append(": the pattern NSN appears here NSNSN\n");
        }
        SearchResult r = search(sb.toString(), "NSN");
        assertEquals(900, r.getMatchCount());
        byte[] pdf = pdfService.generate(r);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int expectedPages = (300 + PdfService.LINES_PER_PAGE - 1) / PdfService.LINES_PER_PAGE;
            assertEquals(expectedPages, doc.getNumberOfPages());
            String extracted = new PDFTextStripper().getText(doc);
            assertTrue(extracted.contains("Line 1: the pattern NSN appears here NSNSN"));
            assertTrue(extracted.contains("Line 300: the pattern NSN appears here NSNSN"));
            assertTrue(extracted.contains("Occurrences: 900"));
        }
        assertHighlightsMatchPositions(r, pdfService.layout(r));
    }

    @Test
    void generatesPdfWhenThereAreNoMatches() throws Exception {
        SearchResult r = search("nothing to see", "zzz");
        try (PDDocument doc = Loader.loadPDF(pdfService.generate(r))) {
            assertEquals(1, doc.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(doc).contains("No occurrences"));
        }
    }

    @Test
    void largeFileProducesPdf() throws Exception {
        String line = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Algorithm analysis UFPS.\n";
        String text = line.repeat(20_000); // ~1.6 MB
        SearchResult r = search(text, "UFPS");
        assertEquals(20_000, r.getMatchCount());
        byte[] pdf = pdfService.generate(r);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() > 300);
        }
        assertHighlightsMatchPositions(r, pdfService.layout(r));
    }
}
