package org.jetbrains.plugins.scala.annotator

import scala.math.Ordered.orderingToOrdered

sealed abstract class Message extends Ordered[Message] {
  def element: String
  def message: String

  override def compare(that: Message): Int =
    (this.element, this.message) compare (that.element, that.message)
}
// TODO: move it to Message companion object
case class Info(override val element: String, override val message: String) extends Message
case class Warning(override val element: String, override val message: String) extends Message
case class Error(override val element: String, override val message: String) extends Message
case class Hint(override val element: String, text: String, override val message: String = "", offsetDelta: Int = 0) extends Message