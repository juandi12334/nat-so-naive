package co.edu.ufps.notsonaive.web;

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
    public SearchResponse search(@RequestParam(value = "file", required = false) MultipartFile file,
                                      @RequestParam(value = "pattern", required = false) String pattern) {
        TextDocument document = fileService.read(file);
        SearchResult result = searchService.search(document, pattern);
        return SearchResponse.of(result, MAX_POSITIONS_IN_RESPONSE, MAX_PREVIEW_CHARS);
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
}
