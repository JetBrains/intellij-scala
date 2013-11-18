package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import java.io.File
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.{FileDeletedEvent, ProgressMessage, CompilerMessage}
import com.intellij.openapi.util.io.FileUtil
import java.util


/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class IdeClient(compilerName: String,
                                 context: CompileContext,
                                 modules: Seq[String],
                                 consumer: OutputConsumer) extends Client {

  private var hasErrors = false

  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    if (kind == Kind.ERROR) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)

    context.processMessage(new CompilerMessage(name, kind, text, sourcePath.orNull,
      -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
  }

  def trace(exception: Throwable) {
    context.processMessage(new CompilerMessage(compilerName, exception))
  }

  def progress(text: String, done: Option[Float]) {
    val formattedText = if (text.isEmpty) "" else {
      val decapitalizedText = text.charAt(0).toLower.toString + text.substring(1)
      "%s: %s [%s]".format(compilerName, decapitalizedText, modules.mkString(", "))
    }
    context.processMessage(new ProgressMessage(formattedText, done.getOrElse(-1.0F)))
  }

  def debug(text: String) {
    ScalaBuilder.Log.info(text)
  }

  def deleted(module: File) {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  def isCanceled = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors
}
