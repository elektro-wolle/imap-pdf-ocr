package de.ideasinlogic.tools.imappdf

data class TextRectangle(
	val xRange: IntRange,
	val yRange: IntRange,
	val text: String
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
}