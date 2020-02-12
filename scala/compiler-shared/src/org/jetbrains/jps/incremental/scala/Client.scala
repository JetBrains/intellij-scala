package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.ClientMsg

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

  def trace(exception: Throwable)

  def progress(text: String, done: Option[Float] = None)

  def debug(text: String)

  def generated(source: File, module: File, name: String)

  def deleted(module: File)

  def isCanceled: Boolean

  def worksheetOutput(text: String): Unit = {}

  def compilationEnd(): Unit = {}

  def processingEnd(): Unit = {}

  /** Used in sbt compile to invalidate every begined source - so after cancel there will be work to recomile */
  def sourceStarted(source: String): Unit = {}
}

object Client {

  final case class ClientMsg(kind: Kind,
                             text: String,
                             source: Option[File],
                             line: Option[Long],
                             column: Option[Long])
}