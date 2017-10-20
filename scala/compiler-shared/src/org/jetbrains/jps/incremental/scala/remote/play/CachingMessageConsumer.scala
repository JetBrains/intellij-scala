package org.jetbrains.jps.incremental.scala.remote.play

import scala.collection._

/**
 * User: Dmitry.Naydanov
 * Date: 13.02.15.
 */
abstract class CachingMessageConsumer(var delegate: MessageConsumer) extends MessageConsumer {//todo refactor me
  val messages = new java.util.concurrent.ConcurrentLinkedQueue[String]

  override def consume(message: String) {
    synchronized {
      messages.add(message)
    }
    if (needFlush(message, messages.size)) flush()
  }

  def flush() {
    synchronized {
      val bs = StringBuilder.newBuilder
      for (i <- 0 until messages.size()) bs append messages.poll() append "\n"
      delegate consume bs.delete(bs.size - 1, bs.size).result()
    }
    
    messages.clear()
  }

  protected def needFlush(msg: String, msgCount: Int): Boolean
}
