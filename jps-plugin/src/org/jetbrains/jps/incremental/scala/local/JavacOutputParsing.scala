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
  private type Location = (File, Long)

  private var location: Option[Location] = None
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
      case LocationPattern(path, row, message) =>
        location = Some((new File(path), row.toLong))
        lines :+= message
      case PointerPattern(prefix) if location.isDefined =>
        val text = (lines :+ line).mkString("\n")
        client.message(kind, text, location.map(_._1), location.map(_._2), Some(1L + prefix.length))
        location = None
        lines = Vector.empty
      case NotePattern(message) =>
        client.message(Kind.WARNING, message)
      case TotalsPattern =>
        // do nothing
      case _ =>
        if (location.isDefined) {
          lines :+= line
        } else {
          client.message(kind, line)
        }
    }
  }
}

object JavacOutputParsing {
  val LocationPattern = "(.*?):(\\d+): (.*)".r
  val PointerPattern = "(\\s*)\\^".r
  val NotePattern = "Note: (.*)".r
  val TotalsPattern = "\\d+ (errors?|warnings?)".r
}
