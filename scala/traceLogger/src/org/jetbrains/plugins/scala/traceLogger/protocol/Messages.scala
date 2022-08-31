package org.jetbrains.plugins.scala.traceLogger
package protocol

import SerializationApi.{ReadWriter => RW, _}
import upickle.implicits.key

/*
final case class TraceLoggerMsg(msg: String,
                                values: Seq[ValueDesc],
                                stackTraceDiff: StackTraceDiff,
                                enclosed: Option[Enclosing])
*/

sealed abstract class TraceLoggerEntry

object TraceLoggerEntry {
  implicit val rw: RW[TraceLoggerEntry] = macroRW

  @key("msg")
  final case class Msg(msg: String, values: Seq[ValueDesc], stackTraceDiff: StackTraceDiff) extends TraceLoggerEntry

  @key("start")
  final case class Start(msg: String, values: Seq[ValueDesc], stackTraceDiff: StackTraceDiff) extends TraceLoggerEntry

  @key("succ")
  final case class Success(result: Data) extends TraceLoggerEntry

  @key("fail")
  final case class Fail(exception: String) extends TraceLoggerEntry

  object Msg {
    implicit val rw: RW[Msg] = macroRW
  }

  object Start {
    implicit val rw: RW[Start] = macroRW
  }

  object Success {
    implicit val rw: RW[Success] = macroRW
  }

  object Fail {
    implicit val rw: RW[Fail] = macroRW
  }
}


final case class StackTraceDiff(base: Int, additional: Seq[StackTraceEntry])

object StackTraceDiff {
  implicit val rw: RW[StackTraceDiff] = macroRW
}


final case class StackTraceEntry(method: String, className: String, line: Int, fileName: String) {
  def toStackTraceElement: StackTraceElement =
    new StackTraceElement(className, method, fileName, line)
}

object StackTraceEntry {
  def from(element: StackTraceElement): StackTraceEntry = {
    StackTraceEntry(element.getMethodName, element.getClassName, element.getLineNumber, element.getFileName)
  }

  // Serialize StackTraceEntry as 3-tuple to have less json output
  implicit val rw: RW[StackTraceEntry] = SerializationApi
    .readwriter[(String, String, Int, String)]
    .bimap[StackTraceEntry](
      e => (e.method, e.className, e.line, e.fileName),
      t => StackTraceEntry(t._1, t._2, t._3, t._4),
    )
}
