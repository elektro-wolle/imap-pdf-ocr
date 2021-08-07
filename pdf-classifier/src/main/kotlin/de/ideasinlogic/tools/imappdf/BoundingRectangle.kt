package de.ideasinlogic.tools.imappdf

import org.apache.pdfbox.text.TextPosition

data class BoundingRectangle(
	val xRange: IntRange,
	val yRange: IntRange
) {
	fun contains(textPosition: TextPosition, scaleDown: Int): Boolean {
		return xRange.contains(textPosition.x.toInt() / scaleDown) &&
			yRange.contains(textPosition.y.toInt() / scaleDown)
	}
}