package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext}
import com.intellij.debugger.engine.managerThread.SuspendContextCommand
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.xdebugger.frame.XCompositeNode
import com.sun.jdi.{InvalidStackFrameException, ObjectCollectedException, VMDisconnectedException}
import org.jetbrains.plugins.scala.debugger.DebuggerBundle

import java.util.concurrent.{CancellationException, CompletableFuture}
import scala.jdk.FunctionConverters._
import scala.util.control.NonFatal

package object util {

  private val LOG = Logger.getInstance(classOf[ScalaClassRenderer])

  implicit class CompletableFutureOps[A](private val cf: CompletableFuture[A]) extends AnyVal {
    def flatMap[B](f: A => CompletableFuture[B]): CompletableFuture[B] =
      cf.thenComposeAsync(f.asJava)

    def map[B](f: A => B): CompletableFuture[B] =
      cf.thenApply(f.asJava)

    def attempt: CompletableFuture[Either[Throwable, A]] =
      cf.handle {
        case (a, null) => Right(a)
        case (_, t) => Left(t)
      }

    def rethrow[AA](implicit ev: A <:< Either[Throwable, AA]): CompletableFuture[AA] =
      cf.flatMap { a =>
        ev(a) match {
          case Left(t) => CompletableFuture.failedFuture(t)
          case Right(a) => CompletableFuture.completedFuture(a)
        }
      }

    def flatTap[B](f: A => CompletableFuture[Unit]): CompletableFuture[A] =
      cf.flatMap(a => f(a).map(_ => a))

    def flatten[AA](implicit ev: A <:< CompletableFuture[AA]): CompletableFuture[AA] =
      cf.flatMap(ev)

    def void: CompletableFuture[Unit] =
      cf.map(_ => ())
  }

  implicit class CompletableFutureTraverseOps[A](private val cfs: Seq[CompletableFuture[A]]) extends AnyVal {
    def sequence: CompletableFuture[Seq[A]] =
      cfs.foldLeft(CompletableFuture.completedFuture(Vector.empty[A])) { (acc, f) =>
        for {
          vec <- acc
          a <- f
        } yield vec :+ a
      }.map(_.toSeq)
  }

  def onDebuggerManagerThread[A](context: SuspendContext)(thunk: => A): CompletableFuture[A] = {
    val future = new CompletableFuture[A]()
    val thread = context.getDebugProcess.getManagerThread
    thread.invokeCommand(new SuspendContextCommand {
      override def getSuspendContext: SuspendContext = context

      override def action(): Unit = {
        try future.complete(thunk)
        catch {
          case t: Throwable =>
            // the future completes whatever happens, its callers would otherwise wait for good
            future.completeExceptionally(t)
            if (!NonFatal(t)) throw t
        }
      }

      override def commandCancelled(): Unit = future.cancel(false)
    })
    future
  }

  def onDebuggerManagerThread[A](context: EvaluationContext)(thunk: => A): CompletableFuture[A] =
    onDebuggerManagerThread(context.getSuspendContext)(thunk)

  def evaluationExceptionFromThrowable(t: Throwable): EvaluateException = t match {
    case e: EvaluateException => e
    case _ => new EvaluateException(t.getMessage)
  }

  /**
   * Builds the children of a debugger tree node with `build` and makes sure that the node finishes even if they cannot
   * be built: when `build` throws, or the future it returns fails or is cancelled, the node shows an error message.
   * Nothing else reports such a failure, the node would stay at "Collecting data" for good.
   *
   * On success, `build` adds the children to the node itself.
   */
  def buildChildrenOrShowError(node: XCompositeNode)(build: => CompletableFuture[Unit]): Unit =
    try {
      build.whenComplete { (_, failure) =>
        if (failure ne null) showChildrenError(node, failure)
      }
    } catch {
      case t: Throwable =>
        showChildrenError(node, t)
        if (t.isInstanceOf[ControlFlowException] || !NonFatal(t)) throw t
    }

  private def showChildrenError(node: XCompositeNode, failure: Throwable): Unit = {
    val cause = Option(DebuggerUtilsAsync.unwrap(failure)).getOrElse(failure)
    // the node is finished before logging, an error logged in tests throws
    if (!node.isObsolete) node.setErrorMessage(childrenErrorMessage(cause))
    cause match {
      case _: ControlFlowException => // must not be logged
      case _ if isExpectedChildrenFailure(cause) => LOG.debug(cause)
      case _ => LOG.error("Cannot build the children of a debugger tree node", cause)
    }
  }

  private def childrenErrorMessage(failure: Throwable): String = failure match {
    case _: CancellationException | _: ProcessCanceledException | _: InvalidStackFrameException | _: VMDisconnectedException =>
      JavaDebuggerBundle.message("error.context.has.changed")
    case e: EvaluateException if e.getMessage ne null => e.getMessage
    case _ => DebuggerBundle.message("error.while.collecting.data", failure.toString)
  }

  // The debuggee and the debugger session may change under the renderer, these failures are not bugs
  private def isExpectedChildrenFailure(failure: Throwable): Boolean = failure match {
    case _: EvaluateException | _: CancellationException | _: InvalidStackFrameException | _: VMDisconnectedException |
         _: ObjectCollectedException => true
    case _ => false
  }
}
