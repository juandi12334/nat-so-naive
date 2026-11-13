package co.edu.ufps.notsonaive.service;

import co.edu.ufps.notsonaive.model.SearchResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;
import ufps.util.colecciones_seed.ListaCD;
import ufps.util.colecciones_seed.Secuencia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Calendar;

/**
 * Generates, with Apache PDFBox, a real PDF document containing the full text
 * of the uploaded file with every occurrence found by Not So Naive highlighted.
 *
 * <p>The body uses the standard Courier font (monospaced), so each character
 * occupies exactly {@code 0.6 * fontSize} points and highlight rectangles are
 * computed exactly from the {@link PdfLayoutEngine} grid. Highlights are drawn
 * semi-transparent, so overlapping occurrences show as a darker overlap.</p>
 */
@Service
public class PdfService {

    static final float PAGE_W = PDRectangle.A4.getWidth();
    static final float PAGE_H = PDRectangle.A4.getHeight();
    static final float BODY_FONT_SIZE = 9f;
    static final float CHAR_W = BODY_FONT_SIZE * 0.6f;   // Courier advance width
    static final float LEADING = 11.5f;
    static final float TEXT_X = 66f;                      // left of text grid (gutter before it)
    static final float RIGHT_MARGIN = 36f;
    static final float BODY_TOP = PAGE_H - 92f;           // first baseline
    static final float BODY_BOTTOM = 52f;
    static final int COLUMNS = (int) Math.floor((PAGE_W - TEXT_X - RIGHT_MARGIN) / CHAR_W);
    static final int LINES_PER_PAGE = (int) Math.floor((BODY_TOP - BODY_BOTTOM) / LEADING) + 1;

    private static final float[] HIGHLIGHT = {1f, 0.80f, 0.10f};
    private static final float[] UNDERLINE = {0.70f, 0.07f, 0.09f};
    private static final float[] INK = {0.10f, 0.10f, 0.12f};
    private static final float[] MUTED = {0.45f, 0.45f, 0.50f};
    private static final float[] BRAND = {0.66f, 0.07f, 0.09f};

    private final PdfLayoutEngine layoutEngine = new PdfLayoutEngine(COLUMNS);

    public PdfLayoutEngine.Layout layout(SearchResult result) {
        return layoutEngine.layout(result.getDocument().content(), result.getPositions(),
                result.getPattern().length());
    }

    /** @return the PDF bytes */
    public byte[] generate(SearchResult result) {
        PdfLayoutEngine.Layout layout = layout(result);
        Secuencia<String> lines = layout.lines();
        int totalPages = Math.max(1, (lines.getTamanio() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);

        // bucket the highlight segments per page (SEED: Secuencia of ListaCD)
        Secuencia<ListaCD<PdfLayoutEngine.Segment>> perPage = new Secuencia<>(totalPages);
        for (int i = 0; i < totalPages; i++) {
            perPage.insertar(new ListaCD<>());
        }
        for (PdfLayoutEngine.Segment s : layout.segments()) {
            perPage.get(s.line() / LINES_PER_PAGE).insertarAlFinal(s);
        }

        PDType1Font courier = new PDType1Font(Standard14Fonts.FontName.COURIER);
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font helveticaBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Not So Naive - occurrences of \"" + result.getPattern() + "\" in "
                    + result.getDocument().fileName());
            info.setAuthor("Juan Diego Rojas Rey - UFPS");
            info.setSubject("Analysis of Algorithms - Not So Naive exact string matching");
            info.setCreator("Not So Naive simulator (Spring Boot + Apache PDFBox)");
            info.setCreationDate(Calendar.getInstance());

            PDExtendedGraphicsState translucent = new PDExtendedGraphicsState();
            translucent.setNonStrokingAlphaConstant(0.55f);

            for (int page = 0; page < totalPages; page++) {
                PDPage pdPage = new PDPage(PDRectangle.A4);
                doc.addPage(pdPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage)) {
                    drawHeader(cs, result, helvetica, helveticaBold);
                    int first = page * LINES_PER_PAGE;
                    int last = Math.min(lines.getTamanio(), first + LINES_PER_PAGE);

                    // 1) highlights under the text
                    cs.saveGraphicsState();
                    cs.setGraphicsStateParameters(translucent);
                    cs.setNonStrokingColor(HIGHLIGHT[0], HIGHLIGHT[1], HIGHLIGHT[2]);
                    for (PdfLayoutEngine.Segment s : perPage.get(page)) {
                        float baseline = baselineOf(s.line() - first);
                        cs.addRect(TEXT_X + s.startCol() * CHAR_W, baseline - 3.6f,
                                (s.endCol() - s.startCol()) * CHAR_W, LEADING - 1f);
                        cs.fill();
                    }
                    cs.restoreGraphicsState();
                    cs.setStrokingColor(UNDERLINE[0], UNDERLINE[1], UNDERLINE[2]);
                    cs.setLineWidth(0.8f);
                    for (PdfLayoutEngine.Segment s : perPage.get(page)) {
                        // alternate the underline height between consecutive occurrences so that
                        // overlapping occurrences remain individually visible
                        float y = baselineOf(s.line() - first) - (s.matchIndex() % 2 == 0 ? 1.6f : 3.1f);
                        cs.moveTo(TEXT_X + s.startCol() * CHAR_W + 0.5f, y);
                        cs.lineTo(TEXT_X + s.endCol() * CHAR_W - 0.5f, y);
                        cs.stroke();
                    }

                    // 2) line numbers and text
                    for (int l = first; l < last; l++) {
                        float baseline = baselineOf(l - first);
                        int logical = layout.logicalLine()[l];
                        String number = logical > 0 ? Integer.toString(logical) : "+"; // "+" = wrapped
                        cs.beginText();
                        cs.setFont(courier, 7f);
                        cs.setNonStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
                        cs.newLineAtOffset(TEXT_X - 6f - courier.getStringWidth(number) / 1000f * 7f, baseline);
                        cs.showText(number);
                        cs.endText();

                        String line = lines.get(l);
                        if (!line.isEmpty()) {
                            cs.beginText();
                            cs.setFont(courier, BODY_FONT_SIZE);
                            cs.setNonStrokingColor(INK[0], INK[1], INK[2]);
                            cs.newLineAtOffset(TEXT_X, baseline);
                            cs.showText(line);
                            cs.endText();
                        }
                    }
                    drawFooter(cs, helvetica, page + 1, totalPages, result.getMatchCount());
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("The PDF could not be generated", e);
        }
    }

    static float baselineOf(int lineOnPage) {
        return BODY_TOP - lineOnPage * LEADING;
    }

    private static void drawHeader(PDPageContentStream cs, SearchResult r, PDType1Font regular, PDType1Font bold)
            throws IOException {
        float top = PAGE_H - 40f;
        cs.setNonStrokingColor(BRAND[0], BRAND[1], BRAND[2]);
        cs.addRect(36f, top + 8f, 4f, -40f);
        cs.fill();

        float maxWidth = PAGE_W - 48f - 36f;
        fitText(cs, bold, 10.5f, INK, 48f, top, maxWidth,
                "FRANCISCO DE PAULA SANTANDER UNIVERSITY  -  Systems Engineering Program");
        fitText(cs, regular, 8.5f, INK, 48f, top - 13f, maxWidth,
                "Course: Analysis of Algorithms  |  Algorithm: Not So Naive  |  Student: Juan Diego Rojas Rey");
        String pattern = r.getPattern().length() > 40 ? r.getPattern().substring(0, 37) + "..." : r.getPattern();
        String file = r.getDocument().fileName().length() > 40
                ? r.getDocument().fileName().substring(0, 37) + "..." : r.getDocument().fileName();
        fitText(cs, regular, 8f, MUTED, 48f, top - 26f, maxWidth,
                "File: " + file + "  |  Pattern: \"" + pattern + "\"  |  Occurrences: " + r.getMatchCount()
                        + "  |  k = " + r.getStatistics().getK() + ", ell = " + r.getStatistics().getEll()
                        + "  |  case-sensitive, 0-based positions");
        cs.setStrokingColor(0.85f, 0.85f, 0.87f);
        cs.setLineWidth(0.6f);
        cs.moveTo(36f, top - 36f);
        cs.lineTo(PAGE_W - 36f, top - 36f);
        cs.stroke();
    }

    private static void drawFooter(PDPageContentStream cs, PDType1Font regular, int page, int total, int matches)
            throws IOException {
        cs.setNonStrokingColor(HIGHLIGHT[0], HIGHLIGHT[1], HIGHLIGHT[2]);
        cs.addRect(36f, 27f, 14f, 8f);
        cs.fill();
        String legend = matches == 0
                ? "No occurrences of the pattern were found in this file."
                : "Highlighted = exact occurrences returned by Not So Naive (overlaps appear darker).";
        text(cs, regular, 7.5f, MUTED, 56f, 28.5f, legend);
        String pg = "Page " + page + " of " + total;
        text(cs, regular, 7.5f, MUTED, PAGE_W - 36f - regular.getStringWidth(pg) / 1000f * 7.5f, 28.5f, pg);
    }

    /** Draws a single line, reducing the font size if needed so it never exceeds maxWidth. */
    static void fitText(PDPageContentStream cs, PDType1Font font, float size, float[] color,
                        float x, float y, float maxWidth, String value) throws IOException {
        float width = font.getStringWidth(value) / 1000f * size;
        float fitted = width > maxWidth ? size * maxWidth / width : size;
        text(cs, font, fitted, color, x, y, value);
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float[] color,
                             float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(value);
        cs.endText();
    }
}
