package de.ideasinlogic.tools.imappdf

import mu.KotlinLogging
import java.io.File
import java.io.IOException

/**
 * Wrapper for the OCR process.
 */
class OCRWrapper {
	/**
	 * Run the OCR.
	 *
	 * @param input an input PDF.
	 * @param lang  language to use.
	 * @return an PDF file with text-annotations, if successful.
	 */
	fun runOCR(input: File, lang: String?): File? {
		return try {
			val output = File.createTempFile("scan-", ".pdf")
			val pb = ProcessBuilder(
				"runocr.sh",
				lang ?: "deu",
				input.absolutePath,
				output.absolutePath
			)
			pb.inheritIO()
			val process = pb.start()
			log.debug { "Started OCR process" }
			val waitFor = process.waitFor()
			if (waitFor != 0) {
				log.warn { "failed to process $waitFor" }
				output.delete()
				return null
			}
			log.debug { "finished process " + waitFor + " and created file " + output.absolutePath }
			output
		} catch (e: InterruptedException) {
			log.debug { "failure during processing " + input.absolutePath }
			return null
		} catch (e: IOException) {
			log.debug { "failure during processing " + input.absolutePath }
			return null
		}
	}

	companion object {
		private val log = KotlinLogging.logger { }
	}
}