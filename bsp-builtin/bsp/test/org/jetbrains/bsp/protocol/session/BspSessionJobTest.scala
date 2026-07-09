package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.{BuildServerCapabilities, MessageType, ShowMessageParams}
import org.jetbrains.bsp.BspTaskCancelled
import org.jetbrains.bsp.protocol.BspNotifications.{BspNotification, ShowMessage}
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BuildServerInfo}
import org.junit.Assert.{assertEquals, assertSame, assertTrue, fail}
import org.junit.Test

import java.util.concurrent.{CancellationException as JavaCancellationException, CompletableFuture, CompletionException, ExecutionException}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * Tests the boundary between Java BSP request futures and the Scala [[jobs.BspSessionJob]] future exposed to [[BspSession]].
 *
 * BSP requests arrive as [[CompletableFuture]] values, and Java reports cancellation through several shapes:
 *  - a cancelled future
 *  - a direct [[JavaCancellationException]]
 *  - future wrappers such as [[CompletionException]] and [[ExecutionException]]
 *
 * These tests assert that all of those cancellation shapes become [[BspTaskCancelled]],
 * so project-close cancellation can stay on the quiet cancellation path instead of being treated as a generic job failure.
 *
 * The non-cancellation cases are covered as well to make sure real BSP failures are still reported as
 * [[Bsp4JJobFailure]] and keep messages accumulated before the request failed.
 */
class BspSessionJobTest {

  @Test
  def directJavaFutureCancellation_CompletesJobAs_BspTaskCancelled(): Unit = {
    val taskFuture = new CompletableFuture[String]
    taskFuture.cancel(true)

    assertSame(BspTaskCancelled, runJobAndGetFailure(taskFuture))
  }

  @Test
  def wrappedJavaFutureCancellation_CompletesJobAs_BspTaskCancelled(): Unit = {
    val taskFuture = new CompletableFuture[String]
    taskFuture.completeExceptionally(
      new CompletionException(new ExecutionException(new JavaCancellationException("cancelled")))
    )

    assertSame(BspTaskCancelled, runJobAndGetFailure(taskFuture))
  }

  @Test
  def cancelRunningJobCancelsTaskFutureAnd_CompletesJobAs_BspTaskCancelled(): Unit = {
    val taskFuture = new CompletableFuture[String]
    val cancellableTaskFuture = CancellableFuture.from(taskFuture)
    val job = createJob(cancellableTaskFuture)

    job.run(null.asInstanceOf[BspServer], BuildServerInfo("test", new BuildServerCapabilities))
    job.cancel()

    assertTrue("Running BSP task future should be cancelled", taskFuture.isCancelled)
    assertSame(BspTaskCancelled, futureFailure(job.future))
  }

  @Test
  def nonCancellationFailure_CompletesJobAs_Bsp4JJobFailure(): Unit = {
    val taskFailure = new IllegalStateException("boom")
    val taskFuture = new CompletableFuture[String]
    taskFuture.completeExceptionally(taskFailure)

    runJobAndGetFailure(taskFuture) match {
      case failure: Bsp4JJobFailure[?] =>
        assertSame(taskFailure, BspJavaFutureFailure.unwrap(failure.error))
        assertEquals(Vector.empty, failure.messages)
      case other =>
        fail(s"Expected Bsp4JJobFailure, got $other")
    }
  }

  @Test
  def nonCancellationFailurePreservesAggregatedMessages(): Unit = {
    val taskFailure = new IllegalStateException("boom")
    val taskFuture = new CompletableFuture[String]
    val job = createJob(taskFuture)

    job.notification(ShowMessage(new ShowMessageParams(MessageType.INFORMATION, "message before failure")))
    job.run(null.asInstanceOf[BspServer], BuildServerInfo("test", new BuildServerCapabilities))
    taskFuture.completeExceptionally(taskFailure)

    futureFailure(job.future) match {
      case failure: Bsp4JJobFailure[?] =>
        assertSame(taskFailure, BspJavaFutureFailure.unwrap(failure.error))
        assertEquals(Vector("message before failure"), failure.messages)
      case other =>
        fail(s"Expected Bsp4JJobFailure, got $other")
    }
  }

  private def runJobAndGetFailure(taskFuture: CompletableFuture[String]): Throwable = {
    val job = createJob(taskFuture)

    job.run(null.asInstanceOf[BspServer], BuildServerInfo("test", new BuildServerCapabilities))
    futureFailure(job.future)
  }

  private def createJob(taskFuture: CompletableFuture[String]): jobs.BspSessionJob[String, Vector[String]] =
    jobs.create[String, Vector[String]](
      (_: BspServer, _: BuildServerInfo) => taskFuture,
      Vector.empty,
      (messages: Vector[String], notification: BspNotification) => notification match {
        case ShowMessage(params) => messages :+ params.getMessage
        case _ => messages
      },
      (_: String) => ()
    )

  private def futureFailure[T](future: Future[T]): Throwable =
    Await.ready(future, 5.seconds).value.get match {
      case Failure(error) => error
      case Success(value) =>
        fail(s"Expected failed future, got $value")
        throw new AssertionError("unreachable")
    }
}
