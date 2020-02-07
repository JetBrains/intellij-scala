package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.{CompilerMessage, FileDeletedEvent, ProgressMessage}


/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class IdeClient(compilerName: String,
                         context: CompileContext,
                         modules: Seq[String],
                         consumer: OutputConsumer) extends Client {

  private var hasErrors = false
  private var lastProgressMessage: String = ""

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, line, column) = msg
    if (kind == Kind.ERROR) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)

    val withoutPointer =
      if (sourcePath.isDefined && line.isDefined && column.isDefined) {
        val lines = text.split('\n')
        lines.filterNot(_.trim == "^").mkString("\n")
      }
      else text
    if (LogFilter.shouldLog(kind, text, source, line, column))
      context.processMessage(new CompilerMessage(name, kind, withoutPointer, sourcePath.orNull,
        -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
  }

  def trace(exception: Throwable) {
    context.processMessage(CompilerMessage.createInternalCompilationError(compilerName, exception))
  }

  def progress(text: String, done: Option[Float]) {
    if (text.nonEmpty) {
      val decapitalizedText = text.charAt(0).toLower.toString + text.substring(1)
      lastProgressMessage = "%s: %s [%s]".format(compilerName, decapitalizedText, modules.mkString(", "))
    }
    context.processMessage(new ProgressMessage(lastProgressMessage, done.getOrElse(-1.0F)))
  }

  def debug(text: String) {
    ScalaBuilder.Log.info(text)
  }

  def deleted(module: File) {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  def isCanceled: Boolean = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors
}
