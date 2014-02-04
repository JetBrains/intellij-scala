package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import xsbti.{F0, Logger}
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import JavacOutputParsing._

/**
 * @author Pavel Fatin
 */
trait JavacOutputParsing extends Logger {
  private case class Header(file: File, line: Long, kind: Kind)

  private var header: Option[Header] = None
  private var lines: Vector[String] = Vector.empty

  protected def client: Client

  abstract override def error(msg: F0[String]) {
    process(msg(), Kind.ERROR)
  }

  abstract override def warn(msg: F0[String]) {
    process(msg(), Kind.PROGRESS)
  }

  // Move Javac output parsing to SBT compiler
  private def process(line: String, kind: Kind) {
    line match {
      case HeaderPattern(path, row, modifier, message) =>
        header = Some(Header(new File(path), row.toLong, if (modifier == null) kind else Kind.WARNING))
        lines :+= message
      case PointerPattern(prefix) if header.isDefined =>
        val text = (lines :+ line).mkString("\n")
        client.message(header.get.kind, text, header.map(_.file), header.map(_.line), Some(1L + prefix.length))
        header = None
        lines = Vector.empty
      case NotePattern(message) =>
        client.message(Kind.WARNING, message)
      case TotalsPattern() =>
        // do nothing
      case _ =>
        if (header.isDefined) {
          lines :+= line
        } else {
          client.message(kind, line)
        }
    }
  }
}

object JavacOutputParsing {
  val HeaderPattern = "(.*?):(\\d+):( warning:)?(.*)".r
  val PointerPattern = "(\\s*)\\^".r
  val NotePattern = "Note: (.*)".r
  val TotalsPattern = "\\d+ (errors?|warnings?)".r
}
