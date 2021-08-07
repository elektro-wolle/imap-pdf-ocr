package de.ideasinlogic.tools.imappdf

import mu.KotlinLogging
import java.awt.image.BufferedImage

class Segmentation(
	private val whiteSpaceMax: Float,
	private val minWhiteSpaceRun: Int,
	private val minSize: Float
) {
	private val log = KotlinLogging.logger {}

	private fun findGapInHistogram(hist: IntArray, range: IntRange): Pair<IntRange, List<IntRange>> {
		val slices = IntArray(hist.size)
		val max = hist.sliceArray(range).maxOrNull()?.toDouble() ?: 0.0

		var left = range.first
		var right = range.last
		while (range.contains(left + 1) && hist[left] / max < whiteSpaceMax) {
			left++
		}
		while (range.contains(right - 2) && hist[right-1] / max < whiteSpaceMax) {
			right--
		}
		if (max < 1 || right - left <= minSize) {
			return Pair(range, emptyList())
		} else {
			var run = 0
			var sliceAt = 0
			for (x in left until right) {
				if (hist[x] / max < whiteSpaceMax) {
					run++
					if (run > slices[sliceAt]) {
						sliceAt = x
					}
				} else {
					run = 0
				}
				slices[x] = run
			}

			if (sliceAt > minSize + left && sliceAt < right - minSize && slices[sliceAt] >= minWhiteSpaceRun) {
				log.trace { "spliced $range into $left,$sliceAt,$right run=${slices[sliceAt]} "}
				return Pair(left..right, listOf(left until sliceAt, sliceAt..right))
			}
			log.trace { "$range trimmed to $left,$right run=${slices[sliceAt]}"}
			return Pair(left..right, emptyList())
		}
	}


	fun segmentize(textFoundAt: BufferedImage, rect: BoundingRectangle): List<BoundingRectangle> {
		log.trace { "try to slice $rect" }
		val xHistogram = IntArray(textFoundAt.width)
		val yHistogram = IntArray(textFoundAt.height)
		for (y in rect.yRange) {
			for (x in rect.xRange) {
				val grayScale = textFoundAt.getRGB(x, y).and(0xff)
				xHistogram[x] += grayScale
				yHistogram[y] += grayScale
			}
		}

		val slices = listOf<BoundingRectangle>().toMutableList()

		val xSlices = findGapInHistogram(xHistogram, rect.xRange)
		val ySlices = findGapInHistogram(yHistogram, rect.yRange)

		val newXrange = xSlices.first
		val newYrange = ySlices.first
		ySlices.second.forEach { sl ->
			slices += segmentize(textFoundAt, BoundingRectangle(newXrange, sl))
		}
		if (slices.isNotEmpty()) {
			return slices
		}
		xSlices.second.forEach { sl ->
			slices += segmentize(textFoundAt, BoundingRectangle(sl, newYrange))
		}
		if (slices.isNotEmpty()) {
			return slices
		}
		return listOf(BoundingRectangle(newXrange, newYrange))
	}

}