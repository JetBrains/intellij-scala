package org.jetbrains.jps.incremental.scala.remote

import java.util.concurrent._

class AsynchEventGenerator(writeEvent: Event => Unit) {

  private val executors = Executors.newFixedThreadPool(1)

  def listener(e: Event): Unit = executors.submit(new Runnable {
    override def run(): Unit = writeEvent(e)
  })

  def complete(): Unit = {
    executors.shutdown()
    executors.awaitTermination(20, TimeUnit.MINUTES)
  }
}