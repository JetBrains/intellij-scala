package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ScheduledDebouncer
import org.jetbrains.plugins.scala.compiler.highlighting.util.FakeScheduledExecutor
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Tag, Test}

import scala.compiletime.uninitialized
import scala.concurrent.duration.*

@Tag(CompilerHighlightingCoreTest)
class ScheduledDebouncerTest:

  private var parentDisposable: Disposable = uninitialized
  private var fakeExecutor: FakeScheduledExecutor = uninitialized
  private var debouncer: ScheduledDebouncer[String] = uninitialized

  @BeforeEach
  def setUp(): Unit =
    parentDisposable = Disposer.newDisposable("ScheduledDebouncerTest")
    fakeExecutor = new FakeScheduledExecutor()
    debouncer = new ScheduledDebouncer[String](
      200.milliseconds,
      fakeExecutor,
      parentDisposable
    )

  @AfterEach
  def tearDown(): Unit = Disposer.dispose(parentDisposable)

  @Test
  def testQueue_SchedulesTask(): Unit =
    var executed = false
    debouncer.queue("file1.scala") {
      executed = true
    }

    assertEquals(1, fakeExecutor.pendingTasks, "Task should be scheduled, but not yet executed")
    assertFalse(executed)

    // Manually run the pending task to simulate time passing instantly
    fakeExecutor.runNextTask()
    assertTrue(executed, "Task should have executed")

  @Test
  def testQueue_ReplacesExistingTask_ForSameKey(): Unit =
    var executionCount = 0
    debouncer.queue("file1.scala") { executionCount += 1 }
    debouncer.queue("file1.scala") { executionCount += 1 }
    debouncer.queue("file1.scala") { executionCount += 1 }

    // Only the last task should remain pending
    assertEquals(1, fakeExecutor.pendingTasks, "Previous tasks for the same key should be cancelled")

    fakeExecutor.runNextTask()
    assertEquals(1, executionCount, "Only the final queued action should execute")

  @Test
  def testQueue_MaintainsSeparateTasks_ForDifferentKeys(): Unit =
    var file1Executed = false
    var file2Executed = false

    debouncer.queue("file1.scala") { file1Executed = true }
    debouncer.queue("file2.scala") { file2Executed = true }

    assertEquals(2, fakeExecutor.pendingTasks, "Tasks for different keys should both be scheduled")

    fakeExecutor.runAllTasks()
    assertTrue(file1Executed)
    assertTrue(file2Executed)

  @Test
  def testDispose_ShutsDownExecutorAndCancelsPending(): Unit =
    var executed = false
    debouncer.queue("file1.scala") { executed = true }
    assertEquals(1, fakeExecutor.pendingTasks)

    Disposer.dispose(parentDisposable)

    assertTrue(fakeExecutor.isShutdownCalled, "Executor should be shut down with the parent")
    assertEquals(0, fakeExecutor.pendingTasks, "Pending tasks should be cancelled on dispose")
    fakeExecutor.runAllTasks()
    assertFalse(executed, "A cancelled task must not run")

  @Test
  def testQueue_AfterDispose_SchedulesNothing(): Unit =
    Disposer.dispose(parentDisposable)

    debouncer.queue("file1.scala") { () }

    assertEquals(0, fakeExecutor.pendingTasks, "Nothing should be scheduled once disposed")
