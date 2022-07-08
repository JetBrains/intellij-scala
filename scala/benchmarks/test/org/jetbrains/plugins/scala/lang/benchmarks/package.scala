package org.jetbrains.plugins.scala.lang

import java.util.{Timer, TimerTask}

import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ThrowableRunnable

package object benchmarks {
  def scheduleShutdown(delayMs: Long) = {
    val exitTask = new TimerTask {
      override def run(): Unit = System.exit(0)
    }
    new Timer().schedule(exitTask, delayMs)
  }

  def syncInEdt(body: => Unit): Unit = EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
    override def run(): Unit = body
  })
}
