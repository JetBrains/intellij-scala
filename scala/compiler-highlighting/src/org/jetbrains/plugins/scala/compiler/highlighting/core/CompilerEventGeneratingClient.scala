package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.remote.{PathTranslator, SerializablePath}
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.compiler.highlighting.core.CompilerMessages
import org.jetbrains.plugins.scala.compiler.highlighting.events.HighlightingPhaseEvents.CompilationPhaseMark
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.SerializableMap
import org.jetbrains.plugins.scala.compiler.highlighting.util.{CompilerHighlightingBundle, VfsUtil}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, EelPathTranslator}
import org.jetbrains.plugins.scala.util.{CanonicalPath, CompilationId}

import java.nio.file.Path

private[highlighting] class CompilerEventGeneratingClient(
  project: Project,
  indicator: ProgressIndicator,
  log: Logger,
  refreshVfs: Boolean,
  documentVersions: SerializableMap[CanonicalPath, Long]
) extends DummyClient {

  override def pathTranslator: PathTranslator = EelPathTranslator

  final val compilationId = CompilationId(timestamp = System.nanoTime(), documentVersions = documentVersions)

  private var hasErrors: Boolean = false

  private var compiled: Set[Path] = Set.empty

  private var reported: Set[Path] = Set.empty

  private var started: Boolean = false

  private var ended: Boolean = false

  private val tracer = Tracing(project)

  indicator.setIndeterminate(false)

  override def progress(@Nls text: String, done: Option[Float]): Unit = {
    indicator.setText(CompilerHighlightingBundle.message("highlighting.compilation.progress", text))
    indicator.setFraction(done.getOrElse(-1.0F).toDouble)
    done.foreach { doneVal =>
      sendEvent(CompilerEvent.ProgressEmitted(compilationId, None, doneVal))
    }
  }

  override def message(msg: Client.ClientMsg): Unit = {
    if (msg.text ne null) {
      // TODO: SCL-22142 Revisit after discussing the changes to fatal warnings in Scala 3.4.1 with the compiler team.
      val description = CompilerMessages.description(msg.text)
      if (CompilerMessages.isNoWarningsCanBeIncurred(description)) {
        return
      }
    }

    msg.kind match {
      case MessageKind.Error | MessageKind.InternalBuilderError => hasErrors = true
      case _ =>
    }
    // Recorded here rather than at `compilationEnd` so that it matches exactly what the store takes from
    // this message: a file the store holds a diagnostic for is a file this compilation reported on.
    msg.source.foreach(source => reported += source.toPath)
    sendEvent(CompilerEvent.MessageEmitted(compilationId, None, None, msg))
  }

  override def compilationStart(): Unit = {
    started = true
    // No JPS build session on the compile-server path.
    sendEvent(CompilerEvent.CompilationStarted(compilationId, None, None, None))
  }

  override def compilationEnd(sources: Set[Path]): Unit = {
    ended = true
    compiled = sources
    if (refreshVfs) {
      VfsUtil.refreshOutputPaths(project, sources)
    }
    sendEvent(CompilerEvent.CompilationFinished(compilationId, None, sources.map(SerializablePath(_, pathTranslator))))
  }

  /**
   * Only used for tracing
   */
  override def compilationPhase(name: String): Unit =
    tracer.mark(compilationId, CompilationPhaseMark(compilationId, name))

  override def isCanceled: Boolean = indicator.isCanceled

  override def trace(exception: Throwable): Unit =
    log.error(s"[${project.getName}] ${exception.getMessage}", exception)

  def successful: Boolean = !hasErrors

  /**
   * Ends a compilation that started but never ended, and returns whether it had to.
   *
   * The compile server sends exactly one `compilationStart` and one `compilationEnd` per request. A compilation
   * the user cancels is left between the two: the IDE stops reading from the socket at the cancel, so the
   * server's `compilationEnd` is never received. Publishing `CompilationFinished` here runs the same cleanup a
   * normal end runs, and the result tells the caller not to treat the compilation as a success or a failure.
   *
   * The output refresh `compilationEnd` performs is skipped: the compilation was interrupted, and it runs a
   * read action that the cancelled indicator would abort.
   */
  def endIfInterrupted(): Boolean = {
    val interrupted = started && !ended
    if (interrupted) {
      ended = true
      sendEvent(CompilerEvent.CompilationFinished(compilationId, None, Set.empty))
    }
    interrupted
  }

  /**
   * The sources this compilation looked at, which is what the build actually did rather than what was asked
   * of it.
   *
   * Two channels, and neither is sufficient. `compilationEnd` names the sources that produced output plus
   * the ones the build system handed over as dirty — under the default incrementality zinc invalidates
   * further on its own, and a file it invalidated and then failed on produced no output and was never
   * dirty, so it appears in neither half. The messages fill that in: a file the compiler reported a
   * diagnostic against is a file it compiled, whatever became of its class files.
   *
   * The union is what "the compiler looked at this file" means, and it is the distinction the decision needs
   * — nothing recorded about a file outside it can be attributed to this compilation. Read on the same
   * thread that drives the compilation, and complete once `compilationEnd` has arrived.
   */
  def compiledSources: Set[Path] = compiled ++ reported

  private def sendEvent(event: CompilerEvent): Unit =
    project.getMessageBus
      .syncPublisher(CompilerEventListener.topic)
      .eventReceived(event)
}