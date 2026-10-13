package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner.{HighlightingRunner, RequestInfo}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.HighlightingRequest
import org.jetbrains.plugins.scala.compiler.highlighting.util.FakeScheduledExecutor
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Tag, Test}

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.concurrent.duration.DurationInt

@Tag(CompilerHighlightingCoreTest)
class HighlightingRunnerTest:

  private var fakeExecutor: FakeScheduledExecutor = uninitialized
  private var fakeProvider: FakeProvider = uninitialized
  private var executedItems: mutable.ListBuffer[RequestInfo] = uninitialized
  private var runner: HighlightingRunner = uninitialized

  // Dummy data for testing
  private val request1: RequestInfo = (null, Seq.empty)
  private val request2: RequestInfo = (null, Seq.empty)

  @BeforeEach
  def setUp(): Unit =
    fakeExecutor = new FakeScheduledExecutor()
    fakeProvider = new FakeProvider()
    executedItems = mutable.ListBuffer.empty

    val executionFunction: RequestInfo => Unit =  req =>
      if req == null then throw new RuntimeException("Simulated unexpected failure")
      executedItems += req

    runner = new HighlightingRunner(
      provider = fakeProvider,
      execution = executionFunction,
      debounceDelay = 300.milliseconds,
      executor = fakeExecutor
    )

  @AfterEach
  def tearDown(): Unit =
    if runner != null then Disposer.dispose(runner)

  @Test
  def testWakeUp_DebouncesMultipleCalls(): Unit =
    runner.wakeUp()
    runner.wakeUp()
    runner.wakeUp()

    // Only the last task should remain uncancelled
    assertEquals(1, fakeExecutor.pendingTasks, "Previous tasks should be cancelled by the debouncer")

    fakeExecutor.runAllTasks()
    assertEquals(0, fakeExecutor.pendingTasks, "Should have executed the single remaining task")

  @Test
  def testTask_DrainsProviderUntilEmpty(): Unit =
    fakeProvider.queue.enqueue(request1, request2)

    runner.wakeUp()
    fakeExecutor.runAllTasks()

    assertEquals(2, executedItems.size, "All items should be drained and executed")
    assertEquals(Seq(request1, request2), executedItems.toSeq)
    assertTrue(fakeProvider.queue.isEmpty, "Provider queue should be empty")

  @Test
  def testTask_ProcessCanceledException_RestoresItem(): Unit =
    // Create a special execution function for this test to throw PCE
    val failingRunner = new HighlightingRunner(
      provider = fakeProvider,
      execution = _ => throw new ProcessCanceledException(),
      debounceDelay = 300.milliseconds,
      executor = fakeExecutor
    )

    fakeProvider.queue.enqueue(request1)
    failingRunner.wakeUp()
    fakeExecutor.runAllTasks()

    assertTrue(executedItems.isEmpty, "Execution should not complete normally")
    assertEquals(Seq(request1), fakeProvider.restored.toSeq, "Failed item should be restored to the provider")

    Disposer.dispose(failingRunner)

  @Test
  def testDispose_ShutsDownExecutorAndCancelsPending(): Unit =
    runner.wakeUp()
    assertEquals(1, fakeExecutor.pendingTasks)

    Disposer.dispose(runner)

    assertTrue(fakeExecutor.isShutdownCalled, "Executor should be shut down")
    assertEquals(0, fakeExecutor.pendingTasks, "Pending tasks should be cancelled on dispose")

  @Test
  def testWakeUp_AfterDispose_SchedulesNothing(): Unit =
    Disposer.dispose(runner)

    runner.wakeUp()

    assertEquals(0, fakeExecutor.pendingTasks, "Nothing should be scheduled once disposed")

  @Test
  def testTask_NonFatalException_RestoresItemAndLogsError(): Unit =
    val failingRunner = new HighlightingRunner(
      provider = fakeProvider,
      execution = _ => throw new IllegalArgumentException("Simulated unexpected failure"),
      debounceDelay = 300.milliseconds,
      executor = fakeExecutor
    )

    fakeProvider.queue.enqueue(request1)
    failingRunner.wakeUp()
    fakeExecutor.runAllTasks()

    assertTrue(executedItems.isEmpty, "Execution should not complete normally")
    assertEquals(Seq(request1), fakeProvider.restored.toSeq, "Failed item should be restored to the provider")

    Disposer.dispose(failingRunner)

  /**
   * Stub provider to control exactly what is polled and observe what gets restored.
   * Restored items are kept in a separate list.
   */
  class FakeProvider extends Provider[HighlightingRequest, RequestInfo]:
    val queue: mutable.Queue[RequestInfo] = mutable.Queue.empty
    val restored: mutable.ListBuffer[RequestInfo] = mutable.ListBuffer.empty

    override def next(): Option[RequestInfo] =
      if queue.nonEmpty then Some(queue.dequeue()) else None

    override def restore(x: RequestInfo): Unit = restored += x

    override def add(v: HighlightingRequest): Unit = ()

    override def dispose(): Unit = ()