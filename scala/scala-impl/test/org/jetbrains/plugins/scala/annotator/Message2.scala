package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange

import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered

/**
 * Practically the same as `Message` but with range and text attributes
 * NOTE: ideally we should consider unifying all tests to use single version of test message
 */
object Message2 {
  case class Info(override val range: TextRange, override val code: String, override val message: String, override val textAttributesKey: TextAttributesKey) extends Message2
  case class Warning(override val range: TextRange, override val code: String, override val message: String, override val textAttributesKey: TextAttributesKey) extends Message2
  case class Error(override val range: TextRange, override val code: String, override val message: String, override val textAttributesKey: TextAttributesKey) extends Message2

  implicit object TextRangeOrdering extends scala.math.Ordering[TextRange] {
    override def compare(x: TextRange, y: TextRange): Int =
      (x.getStartOffset, x.getEndOffset) compare(y.getStartOffset, y.getEndOffset)
  }
}

sealed abstract class Message2 extends Ordered[Message2] {
  /** @return range of annotated code, corresponding */
  def range: TextRange

  /** @return annotated code, corresponding to [[range]] */
  def code: String

  /** @return annotation message */
  def message: String

  def textAttributesKey: TextAttributesKey

  override def compare(that: Message2): Int = {
    import org.jetbrains.plugins.scala.annotator.Message2.TextRangeOrdering

    import scala.math.Ordered.orderingToOrdered

    (this.range, this.message) compare(that.range, that.message)
  }

  def textWithRangeAndCodeAttribute: String = buildText(includeRange = true, includeCode = true, includeAttributes = true)
  def textWithRangeAndAttribute: String = buildText(includeRange = true, includeAttributes = true)
  def textWithRangeAndMessage: String = buildText(includeRange = true, includeMessage = true)

  private def buildText(
    includeRange: Boolean = false,
    includeCode: Boolean = false,
    includeMessage: Boolean = false,
    includeAttributes: Boolean = false,
  ): String = {
    val details = new mutable.ArrayBuffer[String]

    if(includeRange) details.append(range.toString)
    if(includeCode) details.append(code)
    if(includeMessage) details.append(message)
    if(includeAttributes) details.append(textAttributesKey.getExternalName)

    this.getClass.getSimpleName + details.mkString("(", ",", ")")
  }
}