package org.jetbrains.plugins.scala.traceLogViewer.selection

import java.util.concurrent.atomic.AtomicReference
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.Duration

/**
 * Executes scheduled tasks.
 *
 * If a task is scheduled while there is another scheduled task,
 * the earlier task will NOT be executed.
 */
class LastTaskTimer {
  private val timer = new Timer
  private val currentTask = new AtomicReference[TimerTask](null)

  def schedule(time: Duration)(body: => Unit): Unit = {
    val task: TimerTask = new TimerTask {
      override def run(): Unit = {
        if (currentTask.get() == this) {
          body
        }
      }
    }
    currentTask.set(task)
    timer.schedule(task, time.toMillis)
  }
}
