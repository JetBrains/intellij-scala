package org.jetbrains.sbt.shell.communication

import org.jetbrains.sbt.shell.communication.ShellEvent.Output
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

class SbtShellCommandEventProcessorTest {

  @Test
  def outputCollectorProcessIsThreadSafe(): Unit = {
    val collector = new SbtShellCommandEventProcessor.OutputCollector
    val builder = collector.initialResult
    val outputLines = (1 to 1000).map(idx => s"line-$idx")
    val executor = Executors.newFixedThreadPool(8)
    val start = new CountDownLatch(1)

    try {
      outputLines.foreach { line =>
        executor.submit(new Runnable {
          override def run(): Unit = {
            start.await()
            collector.process(builder, Output(line))
          }
        })
      }

      start.countDown()
      executor.shutdown()

      assertTrue("Timed out waiting for output collection tasks", executor.awaitTermination(10, TimeUnit.SECONDS))
    } finally {
      executor.shutdownNow()
    }

    val actualLines = builder.toString().split('\n').filter(_.nonEmpty).toSeq
    assertEquals(outputLines.sorted, actualLines.sorted)
  }
}
