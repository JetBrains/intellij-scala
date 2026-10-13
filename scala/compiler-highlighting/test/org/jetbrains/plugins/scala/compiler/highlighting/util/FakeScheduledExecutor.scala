package org.jetbrains.plugins.scala.compiler.highlighting.util

import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}
import scala.collection.mutable

/**
 * A deterministic test double for ScheduledExecutorService.
 * Returns functional ScheduledFutures so cancellations can be tracked and dropped.
 */
class FakeScheduledExecutor() extends ScheduledThreadPoolExecutor(1):
  private val tasks = mutable.Queue[FakeTask]()
  private var _shutdownCalled = false

  class FakeTask(val command: Runnable) extends ScheduledFuture[Any]:
    var _isCancelled = false

    override def cancel(mayInterruptIfRunning: Boolean): Boolean =
      _isCancelled = true
      true

    override def isCancelled: Boolean = _isCancelled
    override def isDone: Boolean = _isCancelled
    override def get(): Any = null
    override def get(timeout: Long, unit: TimeUnit): Any = null
    override def getDelay(unit: TimeUnit): Long = 0
    override def compareTo(o: java.util.concurrent.Delayed): Int = 0

  override def schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[?] =
    val task = new FakeTask(command)
    tasks.enqueue(task)
    task

  override def shutdown(): Unit =
    _shutdownCalled = true
    super.shutdown()

  def pendingTasks: Int = tasks.count(!_.isCancelled)

  def isShutdownCalled: Boolean = _shutdownCalled

  def runNextTask(): Unit =
    while tasks.nonEmpty do
      val task = tasks.dequeue()
      if !task.isCancelled then
        task.command.run()
        return

  def runAllTasks(): Unit =
    while tasks.nonEmpty do
      val task = tasks.dequeue()
      if !task.isCancelled then
        task.command.run()
