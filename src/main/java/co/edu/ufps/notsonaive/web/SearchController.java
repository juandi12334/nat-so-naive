package co.edu.ufps.notsonaive.web;

import co.edu.ufps.notsonaive.algorithm.TraceStep;
import co.edu.ufps.notsonaive.model.SearchResult;
import co.edu.ufps.notsonaive.model.TextDocument;
import co.edu.ufps.notsonaive.service.FileService;
import co.edu.ufps.notsonaive.service.PdfService;
import co.edu.ufps.notsonaive.service.SearchService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web endpoints. The controller only translates HTTP to service calls;
 * validation lives in {@link FileService}, the search in {@link SearchService}
 * (which uses Not So Naive) and the document in {@link PdfService}.
 *
 * <p>The server is stateless: the browser sends the file and pattern both to
 * search and to generate the PDF, and nothing is stored on disk.</p>
 */
@Controller
public class SearchController {

    /** Positions returned in JSON (the count and the PDF always use all of them). */
    static final int MAX_POSITIONS_IN_RESPONSE = 50_000;
    /** Characters of text returned for the on-screen highlighted preview. */
    static final int MAX_PREVIEW_CHARS = 200_000;

    private final FileService fileService;
    private final SearchService searchService;
    private final PdfService pdfService;

    public SearchController(FileService fileService, SearchService searchService, PdfService pdfService) {
        this.fileService = fileService;
        this.searchService = searchService;
        this.pdfService = pdfService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("maxFileBytes", fileService.getMaxBytes());
        return "index";
    }

    @PostMapping(value = "/api/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> search(@RequestParam(value = "file", required = false) MultipartFile file,
                                      @RequestParam(value = "pattern", required = false) String pattern) {
        TextDocument document = fileService.read(file);
        SearchResult result = searchService.search(document, pattern);
        return toJson(result);
    }

    @PostMapping(value = "/api/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> pdf(@RequestParam(value = "file", required = false) MultipartFile file,
                                      @RequestParam(value = "pattern", required = false) String pattern) {
        TextDocument document = fileService.read(file);
        SearchResult result = searchService.search(document, pattern);
        byte[] pdf = pdfService.generate(result);
        String base = document.fileName().replaceAll("(?i)\\.txt$", "").replaceAll("[^A-Za-z0-9._-]", "_");
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("not-so-naive-" + base + ".pdf", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Match-Count", Integer.toString(result.getMatchCount()))
                .body(pdf);
    }

    private static Map<String, Object> toJson(SearchResult r) {
        TextDocument doc = r.getDocument();
        int count = r.getMatchCount();
        int returned = Math.min(count, MAX_POSITIONS_IN_RESPONSE);
        int[] positions = new int[returned];
        for (int i = 0; i < returned; i++) {
            positions[i] = r.getPositions().get(i);
        }
        boolean previewTruncated = doc.content().length() > MAX_PREVIEW_CHARS;

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("algorithm", "Not So Naive");
        json.put("fileName", doc.fileName());
        json.put("fileSizeBytes", doc.sizeBytes());
        json.put("textLength", doc.content().length());
        json.put("lineCount", doc.lineCount());
        json.put("pattern", r.getPattern());
        json.put("patternLength", r.getPattern().length());
        json.put("matchCount", count);
        json.put("positions", positions);
        json.put("positionsTruncated", returned < count);
        json.put("elapsedMillis", r.getElapsedNanos() / 1_000_000.0);
        json.put("comparisons", r.getStatistics().getComparisons());
        json.put("attempts", r.getStatistics().getAttempts());
        json.put("k", r.getStatistics().getK());
        json.put("ell", r.getStatistics().getEll());
        json.put("notices", doc.notices());
        json.put("text", previewTruncated ? doc.content().substring(0, MAX_PREVIEW_CHARS) : doc.content());
        json.put("previewTruncated", previewTruncated);
        if (r.getStatistics().getTrace() != null) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (TraceStep s : r.getStatistics().getTrace()) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("j", s.j());
                step.put("outcome", s.outcome().name());
                step.put("shift", s.shift());
                step.put("comparisons", s.comparisons());
                steps.add(step);
            }
            json.put("trace", steps);
        }
        return json;
    }
}
