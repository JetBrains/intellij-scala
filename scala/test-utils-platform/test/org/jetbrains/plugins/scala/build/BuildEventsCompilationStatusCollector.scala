package org.jetbrains.plugins.scala.build

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerMessage, CompilerMessageCategory}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * Collects compiler-side diagnostics (`CompileContext`) for a single captured build.
 */
final class BuildEventsCompilationStatusCollector extends CompilationStatusListener {

  import BuildEventsCompilationStatusCollector._

  private val capturedCompilerErrors = new ConcurrentLinkedQueue[String]()
  private val capturedCompilerWarnings = new ConcurrentLinkedQueue[String]()

  def snapshot: Snapshot =
    Snapshot(
      errors = nonEmptyEntries(capturedCompilerErrors),
      warnings = nonEmptyEntries(capturedCompilerWarnings),
    )

  override def compilationFinished(
    aborted: Boolean,
    errors: Int,
    warnings: Int,
    compileContext: CompileContext
  ): Unit = {
    val errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR).toSeq.map(renderCompilerMessage)
    val warningMessages = compileContext.getMessages(CompilerMessageCategory.WARNING).toSeq.map(renderCompilerMessage)

    if (errorMessages.nonEmpty) {
      errorMessages.foreach(capturedCompilerErrors.add)
    } else if (errors > 0) {
      capturedCompilerErrors.add(s"CompileContext reported $errors error(s), but returned no ERROR messages")
    }

    if (warningMessages.nonEmpty) {
      warningMessages.foreach(capturedCompilerWarnings.add)
    } else if (warnings > 0) {
      capturedCompilerWarnings.add(s"CompileContext reported $warnings warning(s), but returned no WARNING messages")
    }

    if (aborted) {
      capturedCompilerErrors.add("CompileContext reported aborted=true")
    }
  }

  private def renderCompilerMessage(message: CompilerMessage): String = {
    val prefix = Option(message.getRenderTextPrefix)
      .orElse(Option(message.getExportTextPrefix))
      .flatMap(BuildDiagnosticsText.normalizedAnsiTextNonempty)
      .getOrElse("")
    val text = BuildDiagnosticsText.normalizedAnsiTextNonempty(message.getMessage).getOrElse("")

    val rendered = s"$prefix$text".trim
    if (rendered.nonEmpty) rendered else "<empty compiler message>"
  }
}

object BuildEventsCompilationStatusCollector {
  final case class Snapshot(
    errors: Seq[String],
    warnings: Seq[String],
  )

  private def nonEmptyEntries(values: java.util.Collection[String]): Seq[String] =
    values.iterator().asScala.filter(_.nonEmpty).toSeq
}
