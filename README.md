# Not So Naive — String Matching Simulator (UFPS)

Universidad Francisco de Paula Santander · Systems Engineering · Analysis of Algorithms
Student: **Juan Diego Rojas Rey** · Algorithm: **Not So Naive**

Web application (Java 21, Spring Boot, Thymeleaf, Apache PDFBox) that searches every occurrence of a
pattern in an uploaded ASCII `.txt` file using a hand-written implementation of the Not So Naive
algorithm, highlights the occurrences on screen and generates a PDF with the same highlights.

- Live: https://nat-so-naive.onrender.com
- Source ZIP: https://nat-so-naive.onrender.com/entrega

## Run in Apache NetBeans
1. *File → Open Project* → select this folder (Maven project).
2. JDK 21. Press *Run* (main class `co.edu.ufps.notsonaive.NotSoNaiveApplication`).
3. Open http://localhost:8080. Tests: *Test Project* or `mvn test`.

## Structure
| Package | Responsibility |
|---|---|
| `algorithm` | `NotSoNaive` (preprocessing k/ell, search phase), `SearchStatistics`, `TraceStep` |
| `service` | `FileService` (ASCII validation), `SearchService`, `PdfService` + `PdfLayoutEngine`, `ProjectArchiveService` |
| `web` | `SearchController` (`/`, `/api/search`, `/api/pdf`), `DeliveryController` (`/entrega`), `GlobalExceptionHandler` |
| `model` | `TextDocument`, `SearchResult` |
| `ufps.util.colecciones_seed` | SEED classes (unmodified, from the UFPS SEED project): `ListaCD`, `NodoD`, `IteratorLCD`, `Secuencia`, `ExceptionUFPS` |

## Conventions
- Exact, case-sensitive search; all occurrences, including overlapping ones.
- Positions are 0-based indexes in the text with line endings normalized to `\n`.
- Only ASCII files are accepted (printable characters, TAB, LF, CR); anything else is rejected with its line and column.
- Patterns are single-line printable ASCII, so an occurrence never crosses a line break. In the PDF, an
  occurrence that crosses a visual wrap of a long line is highlighted in two parts.

## SEED data structures
- `ListaCD<Integer>` collects the positions during the search (`insertarAlFinal` is O(1) thanks to the circular header node).
- `Secuencia<Integer>` (exact capacity) stores the final positions for O(1) indexed access (UI paging and PDF layout).

Reference: C. Charras & T. Lecroq, *Exact String Matching Algorithms*, Not So Naive —
https://www-igm.univ-mlv.fr/~lecroq/string/node13.html
