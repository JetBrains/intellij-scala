package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, PosInfo}
import org.jetbrains.jps.incremental.scala.remote.{CompileServerMetrics, NioPathTranslator, PathTranslator, SerializablePath}
import org.jetbrains.plugins.scala.compiler.diagnostics.Action

import java.nio.file.Path

/**
 * TODO: add documentation with method contracts, currently there are too many methods with vague meaning
 */
trait Client {

  def pathTranslator: PathTranslator = NioPathTranslator

  def message(msg: ClientMsg): Unit

  final def message(kind: MessageKind,
                    @Nls text: String,
                    source: Option[Path] = None,
                    pointer: Option[PosInfo] = None,
                    problemStart: Option[PosInfo] = None,
                    problemEnd: Option[PosInfo] = None,
                    diagnostics: List[Action] = Nil): Unit =
    message(ClientMsg(kind, text, source.map(SerializablePath(_, pathTranslator)), pointer, problemStart, problemEnd, diagnostics))

  final def error(@Nls text: String,
                  source: Option[Path] = None,
                  pointer: Option[PosInfo] = None,
                  problemStart: Option[PosInfo] = None,
                  problemEnd: Option[PosInfo] = None): Unit =
    message(MessageKind.Error, text, source, pointer, problemStart, problemEnd)

  final def warning(@Nls text: String,
                    source: Option[Path] = None,
                    pointer: Option[PosInfo] = None,
                    problemStart: Option[PosInfo] = None,
                    problemEnd: Option[PosInfo] = None): Unit =
    message(MessageKind.Warning, text, source, pointer, problemStart, problemEnd)

  final def info(@Nls text: String,
                 source: Option[Path] = None,
                 pointer: Option[PosInfo] = None,
                 problemStart: Option[PosInfo] = None,
                 problemEnd: Option[PosInfo] = None): Unit =
    message(MessageKind.Info, text, source, pointer, problemStart, problemEnd)

  def trace(exception: Throwable): Unit

  // TODO: extract to bundle carefully, DynamicBundle isn't available in JSP process
  def progress(@Nls text: String, done: Option[Float] = None): Unit

  /** Log info message to the JPS log (build.log) */
  def internalInfo(text: String): Unit

  // TODO: support lazy message calculating: if debug/trace log level are not enabled, do not pass the messages
  //  CS must also know whether e.g. trace level is enabled
  //  So this will require some synchronising of logging level change between IDEA & JPS with CS process.
  /** Log debug message to the JPS log (build.log) */
  def internalDebug(text: String): Unit

  /** Log trace message to the JPS log (build.log) */
  def internalTrace(text: String): Unit

  def generated(source: Path, module: Path, name: String): Unit

  def deleted(module: Path): Unit

  /**
   * The method indicates when a client is interested in canceling the current compilation process.
   * This implies that it should also be stopped on the Scala compiler server.
   *
   * ## Implementation details
   * The actual compilation process on the server is not immediately canceled. It is cooperative and involves multiple parts:
   * 1. The IDE cancels a progress indicator
   * 2. The socket gets closed when the client returns from the read loop.<br>
   *    (see [[remote.RemoteResourceOwner.send]] and [[remote.RemoteResourceOwner.handle]])
   * 3. The server-side output stream reports an error via `PrintStream.checkError`, which makes the server-side client report `isCanceled = true`.<br>
   * 4. The compiler loop polls `isCanceled` and stops the compilation at the next check.
   *    (see [[local.AbstractCompiler.ClientProgress.advance]]),
   *    and JPS-based compilation uses `() => client.isCanceled` as a cancellation callback<br>
   *    (see [[remote.EncodingEventGeneratingClient]]) and [[remote.EventGeneratingClient.isCanceled]])
   */
  def isCanceled: Boolean

  def worksheetOutput(text: String): Unit

  def compilationStart(): Unit

  def compilationPhase(name: String): Unit

  def compilationUnit(path: String): Unit

  def compilationEnd(sources: Set[Path]): Unit

  def processingEnd(): Unit

  /** Used in sbt compile to invalidate every begined source - so after cancel there will be work to recompile */
  def sourceStarted(source: String): Unit

  def metrics(value: CompileServerMetrics): Unit
}

object Client {

  final case class ClientMsg(kind: MessageKind,
                             @Nls text: String,
                             source: Option[SerializablePath],
                             pointer: Option[PosInfo],
                             problemStart: Option[PosInfo],
                             problemEnd: Option[PosInfo],
                             diagnostics: List[Action])

  /**
   * Contains positional information for a highlighting information produced by the Scala compiler. The information
   * contained in this class is supposed to be 1-based, because it is directly consumed by
   * `org.jetbrains.jps.incremental.messages.CompilerMessage`. The Scala compiler reports errors using a 0-based index.
   * This information needs to be adjusted accordingly before storing it in this data structure.
   *
   * @param line 1-based index that represents a line of a physical file
   * @param column 1-based index that represents a column of the line
   */
  final case class PosInfo(line: Int, column: Int)
}
