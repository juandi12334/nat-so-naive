package co.edu.ufps.notsonaive.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile txt(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void homePageShowsAcademicHeader() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UNIVERSIDAD FRANCISCO DE PAULA SANTANDER")))
                .andExpect(content().string(containsString("JUAN DIEGO ROJAS REY")))
                .andExpect(content().string(containsString("NOT SO NAIVE")));
    }

    @Test
    void searchReturnsCountPositionsAndStats() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("banana.txt", "BANANA")).param("pattern", "ANA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("Not So Naive"))
                .andExpect(jsonPath("$.matchCount").value(2))
                .andExpect(jsonPath("$.positions[0]").value(1))
                .andExpect(jsonPath("$.positions[1]").value(3))
                .andExpect(jsonPath("$.fileName").value("banana.txt"))
                .andExpect(jsonPath("$.k").value(1))
                .andExpect(jsonPath("$.ell").value(2))
                .andExpect(jsonPath("$.elapsedMillis").exists())
                .andExpect(jsonPath("$.trace").isArray());
    }

    @Test
    void emptyPatternIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "abc")).param("pattern", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("pattern")));
    }

    @Test
    void nonAsciiFileIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "año")).param("pattern", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("not ASCII")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "")).param("pattern", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pdfEndpointReturnsDownloadablePdf() throws Exception {
        mvc.perform(multipart("/api/pdf").file(txt("demo.txt", "ABABABAB")).param("pattern", "ABA"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Match-Count", "3"));
    }

    @Test
    void deliveryPageAndZip() throws Exception {
        mvc.perform(get("/entrega")).andExpect(status().isOk())
                .andExpect(content().string(containsString("not-so-naive-project.zip")));
        mvc.perform(get("/entrega/not-so-naive-project.zip"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }
}
