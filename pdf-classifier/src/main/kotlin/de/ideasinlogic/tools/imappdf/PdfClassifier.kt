package de.ideasinlogic.tools.imappdf

import mu.KotlinLogging
import org.apache.pdfbox.io.RandomAccessBuffer
import org.apache.pdfbox.pdfparser.PDFParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ByteLookupTable
import java.awt.image.LookupOp
import java.io.File
import java.io.FileInputStream
import java.nio.file.NoSuchFileException
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


class PdfClassifier(
	file: File,
	whiteSpaceMax: Float = 0.04f,
	minWhiteSpaceRun: Int = 5,
	minSizeInMM: Float = 8f,
	private val segmentationWithDPI: Float = 36f
) {
	private val segmentation = Segmentation(whiteSpaceMax, minWhiteSpaceRun, minSizeInMM / 25.4f * segmentationWithDPI)
	private val parser: PDFParser
	private var minG = 255
	private var maxG = 0
	private val SCALE_DOWN = 2

	init {
		if (!file.isFile) {
			throw NoSuchFileException("$file is not a file")
		}

		FileInputStream(file).use {
			parser = PDFParser(RandomAccessBuffer(it))
			parser.parse()
		}

	}

	// Extract text from PDF Document
	fun pdftoText(): Map<Int, List<TextRectangle>> {
		return parser.document.use { cosDoc ->
			PDDocument(cosDoc).use { pdDoc ->
				(0 until pdDoc.numberOfPages).map { currentPage ->
					currentPage to segmentSinglePage(pdDoc, currentPage)
				}.toMap()
			}
		}
	}

	private fun segmentSinglePage(pdDoc: PDDocument, currentPage: Int): List<TextRectangle> {
		// render with double resolution
		val image = PDFRenderer(pdDoc).renderImageWithDPI(currentPage, SCALE_DOWN * segmentationWithDPI)

		// scale down to target DPI
		val grayImg = generateGrayScaleImage(image, currentPage)
		// find min/max for normalization
		findMinMaxValues(grayImg)

		// create 2d Array with ints, where text is found
		val textFoundAt = findTextFragments(grayImg, currentPage)

		// start segmentation with full page
		val rect = BoundingRectangle(0 until grayImg.width, 0 until grayImg.height)

		// find segments
		val segments = segmentation.segmentize(textFoundAt, rect)

		val fragments =
			segments.mapNotNull { segment ->
				extractTextInSegment(currentPage, segment, pdDoc)
					?.let {
						TextRectangle(
							segment.xRange,
							segment.yRange,
							it
						)
					}
			}
		fragments.forEach {
			log.debug { "rect: $it" }
		}
		renderSegments(grayImg, fragments, currentPage)
		return fragments
	}

	private fun renderSegments(img: BufferedImage, segments: List<TextRectangle>, currentPage: Int) {
		val image = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
		(image.graphics as Graphics2D).addRenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON))
		image.graphics.drawImage(img, 0, 0, null)

		val g = image.graphics
		var hue = 0f
		segments.forEachIndexed { idx, bbox ->
			val hsbColor = Color.getHSBColor(hue, 0.6f, 0.5f)
			hue += 0.2f + Random.nextFloat() / 5f
			hue -= hue.toInt()
			g.color = Color(hsbColor.red, hsbColor.green, hsbColor.blue, 50)
			g.fillRect(bbox.getX(), bbox.getY(), bbox.getW(), bbox.getH())
		}
		ImageIO.write(image, "png", File("target/segments-$currentPage.png"))
	}


	private fun findTextFragments(
		grayImg: BufferedImage, currentPage: Int
	): BufferedImage {
		var textFoundAt = arrayOf<Array<Int>>()

		val invert = ByteArray(256)
		for (i in 0..255) {
			invert[i] = (256.0 - ((256.0 * (i - minG)) / (maxG - minG))).coerceIn(0.0, 255.0).toInt().toByte()
		}
		val invertOp: BufferedImageOp = LookupOp(ByteLookupTable(0, invert), null)
		val normalized = invertOp.filter(grayImg, null)

		for (y in 0 until (normalized.height)) {
			var array = arrayOf<Int>()
			for (x in 0 until (normalized.width)) {
				val grayVal = normalized.getRGB(x, y).and(0xff)
				array += grayVal
			}
			textFoundAt += array
		}
		ImageIO.write(normalized, "png", File("target/normalize-$currentPage.png"))
		return normalized
	}

	private fun findMinMaxValues(grayImg: BufferedImage) {
		minG = 255
		maxG = 0
		for (y in 0 until grayImg.height) {
			val line = (0 until grayImg.width).map { grayImg.getRGB(it, y).and(0xff) }
			minG = min(minG, line.minOrNull() ?: 255)
			maxG = max(maxG, line.maxOrNull() ?: 0)
		}
	}

	private fun generateGrayScaleImage(
		image: BufferedImage,
		currentPage: Int
	): BufferedImage {
		val grayImg = BufferedImage(image.width / SCALE_DOWN, image.height / SCALE_DOWN, BufferedImage.TYPE_BYTE_GRAY)
		(grayImg.graphics as Graphics2D).addRenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON))
		grayImg.graphics.drawImage(image, 0, 0, grayImg.width, grayImg.height, null)
		ImageIO.write(grayImg, "png", File("target/gray-$currentPage.png"))
		return grayImg
	}

	private fun extractTextInSegment(
		page: Int,
		segment: BoundingRectangle,
		pdDoc: PDDocument
	): String? {
		val sb = StringBuffer()
		val pdfStripper = object : PDFTextStripper() {
			override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
				textPositions
					?.filter { segment.contains(it, SCALE_DOWN) }
					?.forEach { tp ->
						sb.append(tp.characterCodes.map { c -> c.toChar() }.joinToString(""))
					}
				sb.append(" ")
				super.writeString(text, textPositions)
			}
		}
		pdfStripper.startPage = page + 1
		pdfStripper.endPage = page + 1
		pdfStripper.setShouldSeparateByBeads(true)
		pdfStripper.sortByPosition = false
		// pdfStripper.spacingTolerance = 0.1f
		pdfStripper.getText(pdDoc)
		val str = sb.toString().trim()
		return if (str.isNotBlank()) str else null
	}


	companion object {

		private val log = KotlinLogging.logger {}

		@JvmStatic
		fun main(args: Array<String>) {
			PdfClassifier(File("src/test/resources/Alexander Glas_.pdf")).pdftoText()
		}

	}
}