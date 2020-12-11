package de.ideasinlogic.tools.imappdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Wrapper for the OCR process.
 */
public class OCRWrapper {
  private static Logger log = LoggerFactory.getLogger(OCRWrapper.class);

  /**
   * Run the OCR.
   *
   * @param input an input PDF.
   * @param lang  language to use.
   * @return an PDF file with text-annotations, if successful.
   */
  Optional<File> runOCR(File input, String lang) {
    try {
      File output = File.createTempFile("scan-", ".pdf");
      ProcessBuilder pb = new ProcessBuilder(
          "runocr.sh",
          // "--output-type", "pdf",
          // "--pdf-renderer", "tesseract",
          lang,
          input.getAbsolutePath(),
          output.getAbsolutePath());
      pb.inheritIO();
      Process process = pb.start();
      log.debug("Started OCR process");
      int waitFor = process.waitFor();
      if (waitFor != 0) {
        log.warn("failed to process " + waitFor);
        //noinspection ResultOfMethodCallIgnored
        output.delete();
        return Optional.empty();
      }
      log.debug("finished process " + waitFor + " and created file " + output.getAbsolutePath());
      return Optional.of(output);
    } catch (InterruptedException | IOException e) {
      log.debug("failure during processing " + input.getAbsolutePath());
      return Optional.empty();
    }
  }

}
