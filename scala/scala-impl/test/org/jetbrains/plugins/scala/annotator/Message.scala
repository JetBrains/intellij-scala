package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange

import scala.annotation.nowarn
import scala.math.Ordered.orderingToOrdered

/**
 * See also [[Message2]] for the version with range and text attributes
 */
sealed abstract class Message extends Ordered[Message] {
  def element: String
  def message: String

  override def compare(that: Message): Int =
    (this.element, this.message) compare (that.element, that.message)
}

object Message {
  case class Info(override val element: String, override val message: String) extends Message
  case class Warning(override val element: String, override val message: String) extends Message
  case class Error(override val element: String, override val message: String) extends Message
  case class Hint(override val element: String, text: String, override val message: String = "", offsetDelta: Int = 0) extends Message

  def fromHighlightInfo(info: HighlightInfo, fileText: String): Option[Message] = {
    val constructor = HighlightingSeverityToConstructor.get(info.getSeverity)
    val range = TextRange.create(info.getStartOffset, info.getEndOffset)
    constructor.map(_.apply(range.substring(fileText), info.getDescription))
  }

  @nowarn("cat=deprecation")
  val HighlightingSeverityToConstructor: Map[HighlightSeverity, (String, String) => Message] =
    Map(
      HighlightSeverity.ERROR -> Message.Error.apply,
      HighlightSeverity.WARNING -> Message.Warning.apply,
      HighlightSeverity.WEAK_WARNING -> Message.Warning.apply,
      HighlightSeverity.INFORMATION -> Message.Info.apply,
      HighlightSeverity.INFO -> Message.Info.apply
    )
}
