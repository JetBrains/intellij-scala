package org.jetbrains.plugins.scala.traceLogger

import org.jetbrains.plugins.scala.traceLogger.TraceLogReader.EnclosingResult
import org.jetbrains.plugins.scala.traceLogger.protocol.{SerializationApi, StackTraceDiff, StackTraceEntry, TraceLoggerEntry}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.Source


abstract class TraceLogReader {
  type Node
  type NodeSeq <: Seq[Node]

  protected def newNodeSeqBuilder(): mutable.Builder[Node, NodeSeq]
  protected def createMsgNode(msg: TraceLoggerEntry.Msg, stackTrace: List[StackTraceEntry]): Node
  protected def createEnclosingNode(start: TraceLoggerEntry.Start, inners: NodeSeq,
                                    result: EnclosingResult, stackTrace: List[StackTraceEntry]): Node

  final def readStream(stream: java.io.InputStream): NodeSeq =
    readSource(Source.fromInputStream(stream))

  final def readSource(source: Source): NodeSeq =
    readLines(source.getLines())

  final def readText(text: String): NodeSeq =
    readLines(text.linesIterator)

  final def readLines(lines: Iterator[String]): NodeSeq = {
    import TraceLoggerEntry._

    val applyStackTraceDiff = {
      var currentStackTrace = List.empty[StackTraceEntry]
      (diff: StackTraceDiff) => {
        currentStackTrace = diff.additional ++: currentStackTrace.takeRight(diff.base)
        currentStackTrace
      }
    }

    def convertFrame(): (EnclosingResult, NodeSeq) = {
      val builder = newNodeSeqBuilder()

      @tailrec
      def convertNext(): EnclosingResult = {
        lines.nextOption().map(SerializationApi.read[TraceLoggerEntry](_)) match {
          case None => Left(None)
          case Some(msg: Msg) =>
            val stackTrace = applyStackTraceDiff(msg.stackTraceDiff)
            builder += createMsgNode(msg, stackTrace)
            convertNext()
          case Some(start: Start) =>
            val stackTrace = applyStackTraceDiff(start.stackTraceDiff)
            val (result, inners) = convertFrame()
            builder += createEnclosingNode(start, inners, result, stackTrace)
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