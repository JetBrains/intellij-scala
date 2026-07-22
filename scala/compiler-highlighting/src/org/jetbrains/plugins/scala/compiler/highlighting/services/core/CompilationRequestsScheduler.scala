package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.QueueWaitPhaseEvent.withOutcome
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{QueueRescheduledPhaseEvent, QueueWaitOutcome, QueueWaitPhaseEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, RequestState}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture}
import scala.concurrent.duration.*
import scala.util.control.NonFatal


trait CompilationRequestsScheduler {
  def schedule(request: CompilationRequest): Unit

  def dispose(): Unit
}
object CompilationRequestsScheduler {

  /**
   * What the scheduler does with a request once it is ready.
   */
  type ExecutionFunction = CompilationRequest => Unit

  def apply(project: Project,
            queue: PriorityQueue[CompilationRequest],
            execution: ExecutionFunction,
            executor: ScheduledExecutorService =
            AppExecutorUtil.createBoundedScheduledExecutorService("CompilerHighlightingScheduler", 1))
  : CompilationRequestsScheduler = new CompilationRequestsSchedulerImpl(project, queue, execution, executor)

  private class CompilationRequestsSchedulerImpl(
    project: Project,
    queue: PriorityQueue[CompilationRequest],
    execution: CompilationRequestsScheduler.ExecutionFunction,
    executor: ScheduledExecutorService
  ) extends CompilationRequestsScheduler {

    private val Log = Logger.getInstance(classOf[CompilationRequestsSchedulerImpl])
    private val tracer = Tracing(project)
    private val compilationTask: AtomicReference[ScheduledFuture[?]] = new AtomicReference()

    override def schedule(request: CompilationRequest): Unit = {
      tracer.begin(
        request.id,
        QueueWaitPhaseEvent(request.kind, request.id, request.debugReason,
          request.originFiles.keys.map(_.getName).mkString(", "))
      )
      queue.enqueue(request)
      scheduleNextTask(request.deadline)
    }

    override def dispose(): Unit = {
      executor.shutdown()

      // Close any queue-wait spans still open for requests left in the queue.
      queue.clearAndDispose { request =>
        tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Disposed))
      }
      val task = compilationTask.getAndSet(null)
      if (task ne null) {
        task.cancel(true)
      }
    }

    private def scheduleNextTask(deadline: Deadline): Unit = {
      val Duration(length, unit) = deadline.timeLeft max 1.millisecond: @unchecked
      val future = executor.schedule(new CompilationTask(), length, unit)
      val previous = compilationTask.getAndSet(future)
      if (previous ne null) {
        previous.cancel(false)
      }
    }

    private final class CompilationTask extends Runnable {
      override def run(): Unit = {
        try dequeueAndDispatch()
        finally reschedule()
      }

      private def dequeueAndDispatch(): Unit = {
        queue.dequeueNext() match {
          case Some(request) =>
            request.isReadyForExecution match {
              case RequestState.Ready =>
                try {
                  execution(request)
                } catch {
                  case _: ProcessCanceledException | _: InterruptedException =>
                    // Do not log PCE or InterruptedException.
                    tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Cancelled))
                  case NonFatal(t) =>
                    tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Error))
                    Log.error(s"Execution of compilation request $request failed", t)
                }

              case RequestState.NotReady =>
                val delayed = request.delayed(CompilationRequest.compilationDeadline(project))
                // Mark the reschedule on the still-open span, then keep the span open and follow it onto the
                // delayed copy: same logical request, new deadline.
                tracer.mark(request.id, QueueRescheduledPhaseEvent(request.id, "not ready"))
                tracer.carry(request.id, delayed.id)
                queue.enqueue(delayed)
              case RequestState.Expired =>
                tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Expired))
            }
          case None => // The queue is empty
        }
      }

      private def reschedule(): Unit = {
        queue.peekNext() match {
          case Some(first) => scheduleNextTask(first.deadline)
          case None => // Nothing left in the queue
        }
      }
    }
  }
}


