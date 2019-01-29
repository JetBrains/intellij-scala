package org.jetbrains.plugins.scala.annotator

import scala.math.Ordered.orderingToOrdered

/**
 * Pavel.Fatin, 18.05.2010
 */

sealed abstract class Message extends Ordered[Message] {
  def element: String
  def message: String

  override def compare(that: Message): Int =
    (this.element, this.message) compare (that.element, that.message)
}
case class Info(element: String, message: String) extends Message
case class Warning(element: String, message: String) extends Message
case class Error(element: String, message: String) extends Message