package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.{CompilerMessage, FileDeletedEvent, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.CompilerEvent
import org.jetbrains.plugins.scala.util.CompilationId


/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class IdeClient(compilerName: String,
                         context: CompileContext,
                         modules: Seq[String]) extends Client {

  private var hasErrors = false
  private var lastProgressMessage: String = ""
  protected val compilationId: CompilationId = CompilationId.generate()

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, PosInfo(line, column, _), _) = msg
    if (kind == Kind.ERROR) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)

    if (LogFilter.shouldLog(kind, text, source, line, column)) {
      context.processMessage(new CompilerMessage(name, kind, text, sourcePath.orNull,
        -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
      context.processMessage(CompilerEvent.MessageEmitted(compilationId, msg).toCustomMessage)
    }
  }

  override def compilationStart(): Unit =
    context.processMessage(CompilerEvent.CompilationStarted(compilationId).toCustomMessage)

  override def compilationEnd(sources: Set[File]): Unit =
    context.processMessage(CompilerEvent.CompilationFinished(compilationId, sources).toCustomMessage)

  override def trace(exception: Throwable): Unit =
    context.processMessage(CompilerMessage.createInternalCompilationError(compilerName, exception))

  override def progress(text: String, done: Option[Float]): Unit = {
    if (text.nonEmpty) {
      val decapitalizedText = text.charAt(0).toLower.toString + text.substring(1)
      lastProgressMessage = "%s: %s [%s]".format(compilerName, decapitalizedText, modules.mkString(", "))
    }
    context.processMessage(new ProgressMessage(lastProgressMessage, done.getOrElse(-1.0F)))
    done.foreach { doneVal =>
      context.processMessage(CompilerEvent.ProgressEmitted(compilationId, doneVal).toCustomMessage)
    }
  }

  override def internalInfo(text: String): Unit =
    ScalaBuilder.Log.info(text)

  override def internalDebug(text: String): Unit =
    ScalaBuilder.Log.debug(text)

  override def deleted(module: File): Unit = {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  override def isCanceled: Boolean = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors
}
