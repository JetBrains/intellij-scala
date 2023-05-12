package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.util.TextRange

import scala.math.Ordered.orderingToOrdered

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
}

//////////////////////////////////
//
// Same but with range
//
//////////////////////////////////

object MessageWithRange {
  case class Info(override val range: TextRange, override val code: String, override val message: String) extends MessageWithRange
  case class Warning(override val range: TextRange, override val code: String, override val message: String) extends MessageWithRange
  case class Error(override val range: TextRange, override val code: String, override val message: String) extends MessageWithRange

  implicit object TextRangeOrdering extends scala.math.Ordering[TextRange] {
    override def compare(x: TextRange, y: TextRange): Int =
      (x.getStartOffset, x.getEndOffset) compare (y.getStartOffset, y.getEndOffset)
  }
}

sealed abstract class MessageWithRange extends Ordered[MessageWithRange] {
  /** @return range of annotated code, corresponding */
  def range: TextRange
  /** @return annotated code, corresponding to [[range]] */
  def code: String
  /** @return annotation message */
  def message: String

  override def compare(that: MessageWithRange): Int = {
    import org.jetbrains.plugins.scala.annotator.MessageWithRange.TextRangeOrdering

    import scala.math.Ordered.orderingToOrdered

    (this.range, this.message) compare (that.range, that.message)
  }

  def textWithoutCode: String =
    this.getClass.getSimpleName + s"($range,$message)"
}