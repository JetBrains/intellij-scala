package org.jetbrains.plugins.scala.traceLogger

import org.jetbrains.plugins.scala.traceLogger.TraceLogReader.EnclosingResult
import org.jetbrains.plugins.scala.traceLogger.protocol.{SerializationApi, TraceLoggerEntry}

import scala.annotation.tailrec
import scala.io.Source


abstract class TraceLogReader[+Node] {

  protected[this] def createMsgNode(msg: TraceLoggerEntry.Msg): Node
  protected[this] def createEnclosingNode(start: TraceLoggerEntry.Start, inners: Seq[Node], result: EnclosingResult): Node

  final def readStream(stream: java.io.InputStream): Seq[Node] =
    readSource(Source.fromInputStream(stream))

  final def readSource(source: Source): Seq[Node] =
    readLines(source.getLines())

  final def readText(text: String): Seq[Node] =
    readLines(text.linesIterator)

  final def readLines(lines: Iterator[String]): Seq[Node] = {
    import TraceLoggerEntry._
    def convertFrame(): (EnclosingResult, Seq[Node]) = {
      val builder = Seq.newBuilder[Node]

      @tailrec
      def convertNext(): EnclosingResult = {
        lines.nextOption().map(SerializationApi.read[TraceLoggerEntry](_)) match {
          case None => Left(None)
          case Some(msg: Msg) =>
            builder += createMsgNode(msg)
            convertNext()
          case Some(start: Start) =>
            val (result, inners) = convertFrame()
            builder += createEnclosingNode(start, inners, result)
            convertNext()
          case Some(success: Success) =>
            Right(success)
          case Some(fail: Fail) =>
            Left(Some(fail))
        }
      }

      (convertNext(), builder.result())
    }

    convertFrame()._2
  }
}

object TraceLogReader {
  type EnclosingResult = Either[Option[TraceLoggerEntry.Fail], TraceLoggerEntry.Success]
}