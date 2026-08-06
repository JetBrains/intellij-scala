package org.jetbrains.plugins.scala.build

import com.intellij.build.BuildViewManager
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.task.ProjectTaskManager

/**
 * Captures build diagnostics from both the Build Tool Window event stream and compiler `CompileContext` callbacks.
 */
final class BuildDiagnosticsCollector private(
  disposable: Disposable,
  buildProgressCollector: BuildEventsProgressCollector,
  compilationStatusCollector: BuildEventsCompilationStatusCollector,
) {

  def snapshot: BuildDiagnosticsCollector.Snapshot = {
    val buildProgressSnapshot = buildProgressCollector.snapshot
    val compilationStatusSnapshot = compilationStatusCollector.snapshot

    BuildDiagnosticsCollector.Snapshot(
      buildToolWindowErrors = buildProgressSnapshot.errors,
      buildToolWindowWarnings = buildProgressSnapshot.warnings,
      buildToolWindowRootOutput = buildProgressSnapshot.rootOutput,
      buildToolWindowFinishFailures = buildProgressSnapshot.finishFailures,
      compilerContextErrors = compilationStatusSnapshot.errors,
      compilerContextWarnings = compilationStatusSnapshot.warnings,
    )
  }

  def dispose(): Unit =
    Disposer.dispose(disposable)
}

object BuildDiagnosticsCollector {
  final case class Snapshot(
    buildToolWindowErrors: Seq[String],
    buildToolWindowWarnings: Seq[String],
    buildToolWindowRootOutput: Seq[String],
    buildToolWindowFinishFailures: Seq[String],
    compilerContextErrors: Seq[String],
    compilerContextWarnings: Seq[String],
  ) {
    def rendered: String = {
      val sections = Seq(
        renderSection("Build tool window errors", buildToolWindowErrors),
        renderSection("Build tool window warnings", buildToolWindowWarnings),
        renderSection("Build tool window root output", buildToolWindowRootOutput),
        renderSection("Build tool window finish failures", buildToolWindowFinishFailures),
        renderSection("CompileContext errors", compilerContextErrors),
        renderSection("CompileContext warnings", compilerContextWarnings),
      ).flatten

      if (sections.nonEmpty) sections.mkString("\n", "\n", "\n")
      else "\nNo build diagnostics were captured.\n"
    }

    private def renderSection(title: String, messages: Seq[String]): Option[String] =
      Option(messages)
        .filter(_.nonEmpty)
        .map(_.mkString(s"$title:\n  - ", "\n  - ", ""))
  }

  def start(project: Project, parentDisposable: Disposable): BuildDiagnosticsCollector = {
    val disposable = Disposer.newDisposable("BuildDiagnosticsCollector")
    Disposer.register(parentDisposable, disposable)
    register(project, disposable)
  }

  def capture(project: Project)(action: => ProjectTaskManager.Result): (ProjectTaskManager.Result, Snapshot) = {
    val disposable = Disposer.newDisposable("BuildDiagnosticsCollector.capture")
    val collector = register(project, disposable)
    try {
      val result = action
      (result, collector.snapshot)
    } finally {
      Disposer.dispose(disposable)
    }
  }

  private def register(project: Project, disposable: Disposable): BuildDiagnosticsCollector = {
    val buildProgressCollector = new BuildEventsProgressCollector
    val viewManager = project.getService(classOf[BuildViewManager])
    viewManager.addListener(buildProgressCollector, disposable)

    val compilationStatusCollector = new BuildEventsCompilationStatusCollector
    val busConnection = project.getMessageBus.connect(disposable)
    busConnection.subscribe(CompilerTopics.COMPILATION_STATUS, compilationStatusCollector)

    new BuildDiagnosticsCollector(disposable, buildProgressCollector, compilationStatusCollector)
  }
}

private object BuildDiagnosticsText {
  def normalizedAnsiText(text: String): String =
    stripAnsiCodes(text).trim

  def normalizedAnsiOutputText(text: String): String =
    stripAnsiCodes(text).replace('\r', '\n').trim

  def normalizedAnsiTextNonempty(text: String): Option[String] =
    Option(text).map(normalizedAnsiText).filter(_.nonEmpty)

  private def stripAnsiCodes(message: String): String =
    if (message == null) ""
    else {
      val builder = new StringBuilder()
      new AnsiEscapeDecoder().escapeText(message, ProcessOutputTypes.STDOUT, (text, _) => builder.append(text))
      builder.result()
    }
}
