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
import java.io.File
import java.io.FileInputStream
import java.nio.file.NoSuchFileException
import javax.imageio.ImageIO


class PdfClassifier(val file: File) {
	data class Rect(
		val xRange: IntRange,
		val yRange: IntRange,
		var text: String? = null
	) {
		fun getX(): Int {
			return xRange.first
		}

		fun getY(): Int {
			return yRange.first
		}

		fun getW(): Int {
			return xRange.last - xRange.first
		}

		fun getH(): Int {
			return yRange.last - yRange.first
		}

		fun xSliceAt(xSlice: Int): List<Rect> {
			return if (!xRange.contains(xSlice - 1) || !xRange.contains(xSlice + 1)) {
				log.trace { "$this no  x-slice at $xSlice " }
				emptyList()
			} else {
				log.trace { "$this got x-slice at $xSlice " }
				listOf(
					Rect(xRange.first until xSlice, yRange),
					Rect(xSlice..xRange.last, yRange)
				)
			}
		}

		fun ySliceAt(ySlice: Int): List<Rect> {
			return if (!yRange.contains(ySlice - 1) || !yRange.contains(ySlice + 1)) {
				log.trace { "$this no  y-slice at $ySlice " }
				emptyList()
			} else {
				log.trace { "$this got y-slice at $ySlice " }
				listOf(
					Rect(xRange, yRange.first until ySlice),
					Rect(xRange, ySlice..yRange.last)
				)
			}
		}
	}


	private val parser: PDFParser
	private var minG = 255
	private var maxG = 0

	init {
		if (!file.isFile) {
			throw NoSuchFileException("$file is not a file")
		}

		parser = PDFParser(RandomAccessBuffer(FileInputStream(file)))
		parser.parse()

	}

	// Extract text from PDF Document
	fun pdftoText() {
		parser.document.use { cosDoc ->
			PDDocument(cosDoc).use { pdDoc ->
				(0 until pdDoc.numberOfPages).forEach { currentPage ->
					val image = PDFRenderer(pdDoc).renderImageWithDPI(currentPage, 72f)

					val grayImg = generateGrayScaleImage(image, currentPage)
					findMinMaxValues(grayImg)

					val textFoundAt = findTextFragments(grayImg, currentPage)

					val rect = Rect(textFoundAt[0].indices, textFoundAt.indices)

					val (segments, img) = findSegments(textFoundAt, rect, image, grayImg)

					segments.forEach { segment ->
						segment.text = extractTextInSegment(currentPage, segment, pdDoc)
					}
					val fragments = segments.filter { it.text?.isNotBlank() ?: false }
					fragments.forEach {
						log.debug { "rect: $it" }

					}
					renderSegments(img, fragments, currentPage)

				}
			}
		}
	}

	private fun renderSegments(
		img: BufferedImage,
		segments: List<Rect>,
		currentPage: Int
	) {
		val g = img.graphics
		segments.forEachIndexed { idx, bbox ->
			val hsbColor = Color.getHSBColor(idx.toFloat() / segments.size, 0.6f, 0.5f)
			g.color = Color(hsbColor.red, hsbColor.green, hsbColor.blue, 50)
			g.fillRect(bbox.getX(), bbox.getY(), bbox.getW(), bbox.getH())
		}
		ImageIO.write(img, "png", File("target/segments-$currentPage.png"))
	}

	private fun findSegments(
		textFoundAt: Array<Array<Int>>,
		rect: Rect,
		image: BufferedImage?,
		grayImg: BufferedImage
	): Pair<List<Rect>, BufferedImage> {
		val segments = segmentize(textFoundAt, rect)
		val img = BufferedImage(rect.xRange.last, rect.yRange.last, BufferedImage.TYPE_INT_RGB)
		(img.graphics as Graphics2D).addRenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON))
		img.graphics.drawImage(image, 0, 0, grayImg.width, grayImg.height, null)
		return Pair(segments, img)
	}

	private fun findTextFragments(
		grayImg: BufferedImage,
		currentPage: Int
	): Array<Array<Int>> {
		var textFoundAt = arrayOf<Array<Int>>()
		for (y in 0 until (grayImg.height)) {
			var array = arrayOf<Int>()
			for (x in 0 until (grayImg.width)) {
				val grayVal =
					((maxG - minG) - (grayImg.getRGB(x, y).and(0xff) - minG)) * 255.0 * (255.0 / (maxG - minG))
				array += grayVal.toInt()
				grayImg.setRGB(x, y, grayVal.toInt() * (0x1001001))
			}
			textFoundAt += array
		}
		ImageIO.write(grayImg, "png", File("target/normalize-$currentPage.png"))
		return textFoundAt
	}

	private fun findMinMaxValues(grayImg: BufferedImage) {
		minG = 255
		maxG = 0
		for (y in 0 until grayImg.height) {
			val line = (0 until grayImg.width).map { grayImg.getRGB(it, y).and(0xff) }
			minG = Math.min(minG, line.minOrNull() ?: 255)
			maxG = Math.max(maxG, line.maxOrNull() ?: 0)
		}
	}

	private fun generateGrayScaleImage(
		image: BufferedImage,
		currentPage: Int
	): BufferedImage {
		val grayImg = BufferedImage(image.width / 2, image.height / 2, BufferedImage.TYPE_BYTE_GRAY)
		(grayImg.graphics as Graphics2D).addRenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON))
		grayImg.graphics.drawImage(image, 0, 0, grayImg.width, grayImg.height, null)
		ImageIO.write(grayImg, "png", File("target/gray-$currentPage.png"))
		return grayImg
	}

	private fun extractTextInSegment(
		page: Int,
		segment: Rect,
		pdDoc: PDDocument
	): String {
		val sb = StringBuffer()
		val pdfStripper = object : PDFTextStripper() {
			override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
				textPositions?.forEach { textPosition ->
					if (segment.xRange.contains(textPosition.x.toInt() / 2) &&
						segment.yRange.contains(textPosition.y.toInt() / 2)
					) {
						sb.append(textPosition.characterCodes.map { it.toChar() }.joinToString(""))
					}
				}
				sb.append(" ")
				super.writeString(text, textPositions)
			}
		}
		pdfStripper.startPage = page + 1
		pdfStripper.endPage = page + 1
		pdfStripper.setShouldSeparateByBeads(true)
		pdfStripper.sortByPosition = false
		pdfStripper.spacingTolerance = 0.1f
		pdfStripper.getText(pdDoc)
		return sb.toString().trim()
	}

	private fun findGapInHistogram(hist: Array<Int>, range: IntRange): Int {
		val slices = IntArray(hist.size)
		val max = hist.sliceArray(range).maxOrNull()?.toDouble() ?: 0.0
		if (max < 1 || range.last - range.first < 10) {
			return -1
		} else {
			var run = 0
			var sliceAt = 0
			var left = range.first
			var right = range.last
			while (range.contains(left + 1) && hist[left] / max < 0.03) {
				left++
			}
			while (range.contains(right - 1) && hist[right] / max < 0.03) {
				right--
			}
			for (x in left until right) {
				if (hist[x] / max < 0.03) {
					run++
					if (run > slices[sliceAt]) {
						sliceAt = x
					}
				} else {
					run = 0
				}
				slices[x] = run
			}

			log.debug { "$left/$right -> ${slices.joinToString(",")}" }
//        for (h in 10 downTo 1) {
//          log.debug {
//            hist.indices.map {
//              if (10 * hist[it] / max >= h && range.contains(it)) "X" else {
//                if (it == sliceAt) "|" else " "
//              }
//            }.joinToString("")
//          }
//
//        }
			if (sliceAt > 1 + left && sliceAt < right && slices[sliceAt] > 24)
				return sliceAt - run / 2
		}
		return -1
	}


	private fun segmentize(textFoundAt: Array<Array<Int>>, rect: Rect): List<Rect> {
		log.trace { "try to slice $rect" }
		val xHistogram = Array(textFoundAt[0].size, { 0 })
		val yHistogram = Array(textFoundAt.size, { 0 })
		for (y in rect.yRange) {
			for (x in rect.xRange) {

				xHistogram[x] += textFoundAt[y][x]
				yHistogram[y] += textFoundAt[y][x]

			}
		}
		val slices = listOf<Rect>().toMutableList()

		val xSlice = findGapInHistogram(xHistogram, rect.xRange)
		rect.xSliceAt(xSlice).forEach {
			slices += segmentize(textFoundAt, it)
		}
		if (slices.isNotEmpty()) {
			return slices
		}

		val ySlice = findGapInHistogram(yHistogram, rect.yRange)
		rect.ySliceAt(ySlice).forEach {
			slices += segmentize(textFoundAt, it)
		}
		if (slices.isNotEmpty()) {
			return slices
		}



		return listOf(rect)
	}

	companion object {

		private val log = KotlinLogging.logger {}

		@JvmStatic
		fun main(args: Array<String>) {
			PdfClassifier(File("src/test/resources/scan-kk.pdf")).pdftoText()
		}

	}
}