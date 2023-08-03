package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, PosInfo}
import org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics

import java.io.File

/**
 * TODO: add documentation with method contracts, currently there are too many methods with vague meaning
 */
trait Client {

  def message(msg: ClientMsg): Unit

  final def message(kind: MessageKind,
                    @Nls text: String,
                    source: Option[File] = None,
                    pointer: Option[PosInfo] = None,
                    problemStart: Option[PosInfo] = None,
                    problemEnd: Option[PosInfo] = None): Unit =
    message(ClientMsg(kind, text, source, pointer, problemStart, problemEnd))

  final def error(@Nls text: String,
                  source: Option[File] = None,
                  pointer: Option[PosInfo] = None,
                  problemStart: Option[PosInfo] = None,
                  problemEnd: Option[PosInfo] = None): Unit =
    message(MessageKind.Error, text, source, pointer, problemStart, problemEnd)

  final def warning(@Nls text: String,
                    source: Option[File] = None,
                    pointer: Option[PosInfo] = None,
                    problemStart: Option[PosInfo] = None,
                    problemEnd: Option[PosInfo] = None): Unit =
    message(MessageKind.Warning, text, source, pointer, problemStart, problemEnd)

  final def info(@Nls text: String,
                 source: Option[File] = None,
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

  def generated(source: File, module: File, name: String): Unit

  def deleted(module: File): Unit

  def isCanceled: Boolean

  def worksheetOutput(text: String): Unit

  def compilationStart(): Unit

  def compilationPhase(name: String): Unit

  def compilationUnit(path: String): Unit

  def compilationEnd(sources: Set[File]): Unit

  def processingEnd(): Unit

  /** Used in sbt compile to invalidate every begined source - so after cancel there will be work to recompile */
  def sourceStarted(source: String): Unit

  def metrics(value: CompileServerMetrics): Unit
}

object Client {

  final case class ClientMsg(kind: MessageKind,
                             @Nls text: String,
                             source: Option[File],
                             pointer: Option[PosInfo],
                             problemStart: Option[PosInfo],
                             problemEnd: Option[PosInfo])

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
