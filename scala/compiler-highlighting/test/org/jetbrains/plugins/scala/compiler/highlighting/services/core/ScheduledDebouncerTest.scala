package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ScheduledDebouncer
import org.junit.{After, Assert, Before, Test}

import java.util.concurrent.{ScheduledFuture, TimeUnit}
import scala.compiletime.uninitialized
import scala.concurrent.duration.*

class ScheduledDebouncerTest:

  private var parentDisposable: Disposable = uninitialized
  private var fakeExecutor: FakeScheduledExecutor = uninitialized
  private var debouncer: ScheduledDebouncer[String] = uninitialized

  @Before
  def setUp(): Unit =
    parentDisposable = Disposer.newDisposable("ScheduledDebouncerTest")
    fakeExecutor = new FakeScheduledExecutor()
    debouncer = new ScheduledDebouncer[String](
      200.milliseconds,
      fakeExecutor,
      parentDisposable
    )

  @After
  def tearDown(): Unit = Disposer.dispose(parentDisposable)

  @Test
  def testQueue_SchedulesTask(): Unit =
    var executed = false
    debouncer.queue("file1.scala") {
      executed = true
    }

    Assert.assertEquals("Task should be scheduled, but not yet executed", 1, fakeExecutor.pendingTasks)
    Assert.assertFalse(executed)

    // Manually run the pending task to simulate time passing instantly
    fakeExecutor.runNextTask()
    Assert.assertTrue("Task should have executed", executed)

  @Test
  def testQueue_ReplacesExistingTask_ForSameKey(): Unit =
    var executionCount = 0
    debouncer.queue("file1.scala") { executionCount += 1 }
    debouncer.queue("file1.scala") { executionCount += 1 }
    debouncer.queue("file1.scala") { executionCount += 1 }

    // Only the last task should remain pending
    Assert.assertEquals("Previous tasks for the same key should be cancelled", 1, fakeExecutor.pendingTasks)

    fakeExecutor.runNextTask()
    Assert.assertEquals("Only the final queued action should execute", 1, executionCount)

  @Test
  def testQueue_MaintainsSeparateTasks_ForDifferentKeys(): Unit =
    var file1Executed = false
    var file2Executed = false

    debouncer.queue("file1.scala") { file1Executed = true }
    debouncer.queue("file2.scala") { file2Executed = true }

    Assert.assertEquals("Tasks for different keys should both be scheduled", 2, fakeExecutor.pendingTasks)

    fakeExecutor.runAllTasks()
    Assert.assertTrue(file1Executed)
    Assert.assertTrue(file2Executed)

/**
 * A minimal test double for ScheduledExecutorService to run tasks deterministically.
 */
class FakeScheduledExecutor extends java.util.concurrent.ScheduledThreadPoolExecutor(1):
  private val tasks = scala.collection.mutable.Queue[FakeTask]()

  // Wrap the runnable in a dummy future that tracks cancellation
  class FakeTask(val command: Runnable) extends ScheduledFuture[Any]:
    var isCancelled = false

    override def cancel(mayInterruptIfRunning: Boolean): Boolean =
      isCancelled = true
      true

    override def isDone: Boolean = isCancelled
    override def get(): Any = null
    override def get(timeout: Long, unit: TimeUnit): Any = null
    override def getDelay(unit: TimeUnit): Long = 0
    override def compareTo(o: java.util.concurrent.Delayed): Int = 0

  override def schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[?] =
    val task = new FakeTask(command)
    tasks.enqueue(task)
    task

  // Only count tasks that haven't been cancelled
  def pendingTasks: Int = tasks.count(!_.isCancelled)

  def runNextTask(): Unit =
    while (tasks.nonEmpty) {
      val task = tasks.dequeue()
      if (!task.isCancelled) {
        task.command.run()
        return
      }
    }

  def runAllTasks(): Unit =
    while (tasks.nonEmpty) {
      val task = tasks.dequeue()
      if (!task.isCancelled) {
        task.command.run()
      }
    }