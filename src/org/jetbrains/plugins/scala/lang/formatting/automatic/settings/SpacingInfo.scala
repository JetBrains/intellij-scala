package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import java.util.regex.Pattern

/**
 * @author Roman.Shein
 *         Date: 16.01.14
 */
class SpacingInfo(val spacesCount: Int, val minLineBreaksCount: Option[Int], val maxLineBreaksCount: Option[Int], val lineBreaksCount: Int) {

  /**
   * Creates new spacing info based on this with parameters modified to comply with the other.
   * @param other
   * @return
   */
  def devour(other: SpacingInfo):SpacingInfo = {
    new SpacingInfo(spacesCount,
      minLineBreaksCount.map(Math.max(_, other.minLineBreaksCount.getOrElse(0))),
      maxLineBreaksCount.map(Math.min(_, other.maxLineBreaksCount.getOrElse(Integer.MAX_VALUE))), lineBreaksCount)
  }

  override def toString: String = "Spacing: " + spacesCount + " newLines: min " +
    minLineBreaksCount.map(_.toString).getOrElse("*") + " max " + maxLineBreaksCount.map(_.toString).getOrElse("* actual ") +
    lineBreaksCount

  override def equals(other :Any) = other match {
    case info: SpacingInfo => info.spacesCount == spacesCount &&
            info.spacesCount == minLineBreaksCount &&
            info.maxLineBreaksCount  == maxLineBreaksCount &&
            info.lineBreaksCount == lineBreaksCount
    case _ => false
  }
}

object SpacingInfo {
  def apply(spacing: String): SpacingInfo = new SpacingInfo(if (spacing.contains("\n")) spacing.substring(0, spacing.indexOf("\n")).length else spacing.length, None, None, {
    val matcher = Pattern.compile("(\n)").matcher(spacing)
    var newLinesCount = 0
    while (matcher.find()) {
      newLinesCount += 1
    }
    newLinesCount
  })
}