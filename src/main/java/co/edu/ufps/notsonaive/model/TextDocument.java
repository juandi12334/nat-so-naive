package co.edu.ufps.notsonaive.model;

import java.util.List;

/**
 * A validated ASCII text file, ready to be searched.
 *
 * @param fileName  original file name sent by the browser
 * @param sizeBytes original size in bytes
 * @param content   text with line endings normalized to LF ("\n")
 * @param lineCount number of lines in the normalized text
 * @param notices   informational messages about transformations applied
 *                  (e.g. CRLF normalization); empty when none
 */
public record TextDocument(String fileName, long sizeBytes, String content, int lineCount,
                           List<String> notices) {
}
