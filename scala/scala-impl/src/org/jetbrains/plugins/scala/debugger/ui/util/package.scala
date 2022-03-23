package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext}
import com.intellij.debugger.engine.managerThread.SuspendContextCommand

import java.util.concurrent.CompletableFuture
import scala.jdk.FunctionConverters._

package object util {

  implicit class CompletableFutureOps[A](private val cf: CompletableFuture[A]) extends AnyVal {
    def flatMap[B](f: A => CompletableFuture[B]): CompletableFuture[B] =
      cf.thenCompose(f.asJava)

    def map[B](f: A => B): CompletableFuture[B] =
      cf.thenApply(f.asJava)
  }

  def onDebuggerManagerThread[A](context: EvaluationContext)(thunk: => A): CompletableFuture[A] = {
    val future = new CompletableFuture[A]()
    val thread = context.getDebugProcess.getManagerThread
    val suspendContext = context.getSuspendContext
    thread.invokeCommand(new SuspendContextCommand {
      override def getSuspendContext: SuspendContext = suspendContext

      override def action(): Unit = {
        try future.complete(thunk)
        catch {
          case e: EvaluateException => future.completeExceptionally(e)
        }
      }

      override def commandCancelled(): Unit = future.cancel(false)
    })
    future
  }
}
