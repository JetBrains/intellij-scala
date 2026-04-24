package org.jetbrains.sbt.shell.build.util

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerMessage, CompilerMessageCategory}
import org.jetbrains.plugins.scala.build.BuildMessages

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Collects compiler-side diagnostics (`CompileContext`) for a single captured build.
 *
 * TODO Move to a common compiler/build-test module.<br>
 *  This collector is not sbt/sbt-shell specific and can be reused by other build-related integration tests.
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
      .flatMap(normalizedAnsiTextNonempty)
      .getOrElse("")
    val text = normalizedAnsiTextNonempty(message.getMessage).getOrElse("")

    val rendered = s"$prefix$text".trim
    if (rendered.nonEmpty) rendered else "<empty compiler message>"
  }

  private def normalizedAnsiTextNonempty(text: String): Option[String] =
    Option(text)
      .map(BuildMessages.stripAnsiCodes)
      .map(_.trim)
      .filter(_.nonEmpty)
}

object BuildEventsCompilationStatusCollector {
  final case class Snapshot(
    errors: Seq[String],
    warnings: Seq[String],
  )
}
