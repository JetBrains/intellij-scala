package org.jetbrains.bsp.protocol.session

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

private abstract class Cancelable {
  def cancel(): Unit
}

private object Cancelable {
  def apply(fn: () => Unit): Cancelable = () => fn()

  val empty: Cancelable = Cancelable(() => ())

  def cancelAll(iterable: Iterable[Cancelable]): Unit = {
    val errors = ListBuffer.empty[Throwable]
    iterable.foreach { cancelable =>
      try cancelable.cancel()
      catch {
        case NonFatal(ex) =>
          errors += ex
      }
    }
    errors.toList match {
      case head :: tail =>
        tail.foreach { e =>
          if (e ne head) {
            head.addSuppressed(e)
          }
        }
        throw head
      case _ =>
    }
  }
}