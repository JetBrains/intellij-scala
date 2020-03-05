package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, CompileServerState}

/**
 * TODO: add documentation with method contracts, currently there are too many methods with vague meaning
 *
 * @author Pavel Fatin
 */
trait Client {

  def message(msg: ClientMsg): Unit

  final def message(kind: Kind,
                    text: String,
                    source: Option[File] = None,
                    line: Option[Long] = None,
                    column: Option[Long] = None): Unit =
    message(ClientMsg(kind, text, source, line, column))

  final def error(text: String,
                  source: Option[File] = None,
                  line: Option[Long] = None,
                  column: Option[Long] = None): Unit =
    message(Kind.ERROR, text, source, line, column)

  final def warning(text: String,
                    source: Option[File] = None,
                    line: Option[Long] = None,
                    column: Option[Long] = None): Unit =
    message(Kind.WARNING, text, source, line, column)

  final def info(text: String,
                 source: Option[File] = None,
                 line: Option[Long] = None,
                 column: Option[Long] = None): Unit =
    message(Kind.INFO, text, source, line, column)

  def trace(exception: Throwable): Unit

  def progress(text: String, done: Option[Float] = None): Unit

  def internalInfo(text: String): Unit

  def internalDebug(text: String): Unit

  def generated(source: File, module: File, name: String): Unit

  def deleted(module: File): Unit

  def isCanceled: Boolean

  def worksheetOutput(text: String): Unit

  def compilationEnd(sources: Set[File]): Unit

  def processingEnd(): Unit

  /** Used in sbt compile to invalidate every begined source - so after cancel there will be work to recomile */
  def sourceStarted(source: String): Unit

  def compileServerState(state: CompileServerState): Unit
}

object Client {

  final case class ClientMsg(kind: Kind,
                             text: String,
                             source: Option[File],
                             line: Option[Long],
                             column: Option[Long])

  final case class CompileServerState(compilingNow: Boolean)
}