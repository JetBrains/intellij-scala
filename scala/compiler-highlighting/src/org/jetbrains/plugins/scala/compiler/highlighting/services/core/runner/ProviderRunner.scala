package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Provider

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait ProviderRunner[T](provider: Provider[?, T],
                        executionFunction: T => Unit) extends TaskRunner:

  @tailrec
  private def execute(): Unit = 
    Log.debug("Runner waking up")
    provider.next() match
      case Some(x) =>
        val executionResult = Try:
          executionFunction(x)

        executionResult match
          case Success(_) =>
            execute()

          case Failure(_: ProcessCanceledException | _: InterruptedException) =>
            Log.warn(s"Process was canceled for $x")
            provider.restore(x)

          case Failure(NonFatal(t)) =>
            Log.warn(s"Execution of compilation request $x failed because: ${getMessage(t)}")
            provider.restore(x)

          case Failure(t) =>
            Log.warn(s"Execution of compilation request $x failed because: ${getMessage(t)}")
            provider.restore(x)
            
      case None =>
        Log.debug("Runner going Idle")

  private def getMessage(t: Throwable) = 
    val msg = if (t.getCause != null) t.getCause.getMessage else t.getMessage
    msg

  override protected def task(): Runnable = () =>
    Log.debug("Runner waking up")
    execute()
