package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, PosInfo}
import org.jetbrains.jps.incremental.scala.remote.{CompileServerMeteringInfo, CompileServerMetrics}

/**
 * TODO: add documentation with method contracts, currently there are too many methods with vague meaning
 *
 * @author Pavel Fatin
 */
trait Client {

  def message(msg: ClientMsg): Unit

  final def message(kind: Kind,
                    @Nls text: String,
                    source: Option[File] = None,
                    from: PosInfo = PosInfo.Empty,
                    to: PosInfo = PosInfo.Empty): Unit =
    message(ClientMsg(kind, text, source, from, to))

  final def error(@Nls text: String,
                  source: Option[File] = None,
                  from: PosInfo = PosInfo.Empty,
                  to: PosInfo = PosInfo.Empty): Unit =
    message(Kind.ERROR, text, source, from, to)

  final def warning(@Nls text: String,
                    source: Option[File] = None,
                    from: PosInfo = PosInfo.Empty,
                    to: PosInfo = PosInfo.Empty): Unit =
    message(Kind.WARNING, text, source, from, to)

  final def info(@Nls text: String,
                 source: Option[File] = None,
                 from: PosInfo = PosInfo.Empty,
                 to: PosInfo = PosInfo.Empty): Unit =
    message(Kind.INFO, text, source, from, to)

  def trace(exception: Throwable): Unit

  // TODO: extract to bundle carefully, DynamicBundle isn't available in JSP process
  def progress(text: String, done: Option[Float] = None): Unit

  def internalInfo(text: String): Unit

  def internalDebug(text: String): Unit

  def generated(source: File, module: File, name: String): Unit

  def deleted(module: File): Unit

  def isCanceled: Boolean

  def worksheetOutput(text: String): Unit

  def compilationStart(): Unit
  
  def compilationEnd(sources: Set[File]): Unit

  def processingEnd(): Unit

  /** Used in sbt compile to invalidate every begined source - so after cancel there will be work to recomile */
  def sourceStarted(source: String): Unit

  def meteringInfo(info: CompileServerMeteringInfo): Unit // TODO replace with metrics

  def metrics(value: CompileServerMetrics): Unit
}

object Client {

  final case class ClientMsg(kind: Kind,
                             @Nls text: String,
                             source: Option[File],
                             from: PosInfo,
                             to: PosInfo)

  /**
   * Information about the position in the source file.
   */
  final case class PosInfo(line: Option[Long],
                           column: Option[Long],
                           offset: Option[Long])
  
  object PosInfo {
    
    final val Empty: PosInfo = PosInfo(None, None, None)
  }
}