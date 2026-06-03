package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange

import scala.annotation.nowarn
import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered

/**
 * See also [[Message]].
 * This class is practically the same as [[Message]] but with range and text attributes
 * NOTE: ideally we should consider unifying all tests to use a single version of the test message class
 */
case class Message2(
  level: HighlightSeverity,
  //TODO: let's replace range this line number+column...
  // it's pretty difficult to see in the test files exactly where a message is
  range: TextRange,
  code: String,
  message: String,
  textAttributesKey: TextAttributesKey,
  fixes: Seq[CommonIntentionAction]
) extends Ordered[Message2] {

  override def compare(that: Message2): Int = {
    import org.jetbrains.plugins.scala.annotator.Message2.TextRangeOrdering

    import scala.math.Ordered.orderingToOrdered
    (this.range, Option(this.message)).compare((that.range, Option(that.message)))
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
    if (includeRange) details.append(range.toString)
    if (includeCode) details.append(code)
    if (includeMessage) details.append(message)
    if (includeAttributes) details.append(textAttributesKey.getExternalName)

    //noinspection ScalaDeprecation
    @nowarn("cat=deprecation")
    val levelAsString = level match {
      case HighlightSeverity.ERROR => "Error"
      case HighlightSeverity.WARNING => "Warning"
      case HighlightSeverity.WEAK_WARNING => "Warning"
      case HighlightSeverity.INFORMATION => "Info"
      case HighlightSeverity.INFO => "Info"
      case HighlightInfoType.SYMBOL_TYPE_SEVERITY => "Info"
      case other => other.toString
    }
    levelAsString + details.mkString("(", ",", ")")
  }
}

object Message2 {
  def Info(range: TextRange, code: String, message: String, textAttributesKey: TextAttributesKey, fixes: Seq[CommonIntentionAction]): Message2 =
    Message2(HighlightSeverity.INFORMATION, range, code, message, textAttributesKey, fixes)
  def Warning(range: TextRange, code: String, message: String, textAttributesKey: TextAttributesKey, fixes: Seq[CommonIntentionAction]): Message2 =
    Message2(HighlightSeverity.WARNING, range, code, message, textAttributesKey, fixes)
  def Error(range: TextRange, code: String, message: String, textAttributesKey: TextAttributesKey, fixes: Seq[CommonIntentionAction]): Message2 =
    Message2(HighlightSeverity.ERROR, range, code, message, textAttributesKey, fixes)

  implicit object TextRangeOrdering extends scala.math.Ordering[TextRange] {
    override def compare(x: TextRange, y: TextRange): Int =
      (x.getStartOffset, x.getEndOffset) compare(y.getStartOffset, y.getEndOffset)
  }
}