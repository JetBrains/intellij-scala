package org.jetbrains.jps.incremental.scala
package local

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.JavacOutputParsing._
import xsbti.Logger
import java.util.function.Supplier

/**
 * @author Pavel Fatin
 */
trait JavacOutputParsing extends Logger {
  private case class Header(file: File, line: Long, kind: Kind)

  private var header: Option[Header] = None
  private var lines: Vector[String] = Vector.empty

  protected def client: Client

  abstract override def error(msg: Supplier[String]) {
    process(msg.get(), Kind.ERROR)
  }

  abstract override def warn(msg: Supplier[String]) {
    process(msg.get(), Kind.PROGRESS)
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
