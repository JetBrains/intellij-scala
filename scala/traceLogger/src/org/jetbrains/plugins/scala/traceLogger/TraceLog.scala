package org.jetbrains.plugins.scala.traceLogger

import org.jetbrains.plugins.scala.traceLogger.protocol._

import java.io.FileWriter
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

abstract class TraceLogWriter {
  def log(msg: String, values: Seq[ValueDesc], st: StackTrace): Unit
  def startEnclosing(msg: String, args: Seq[ValueDesc], st: StackTrace): Unit
  def enclosingSuccess(result: Data, st: StackTrace): Unit
  def enclosingFail(exception: Throwable, st: StackTrace): Unit

  def close(): Unit = ()
}

object TraceLog {
  private val activeLoggers = new AtomicInteger
  private val loggers = ThreadLocal.withInitial[TraceLogWriter](() => NoOpLogger)

  def isActiveInAtLeastOneThread: Boolean = activeLoggers.get() > 0

  def inst: TraceLogWriter = loggers.get()

  def runWithTraceLogger[T](topic: String, logWriter: String => TraceLogWriter = TraceLog.createTraceLogWriter)(body: => T): T = {
    val logger = logWriter(topic)
    try {
      loggers.set(logger)
      activeLoggers.incrementAndGet()

      body
    } finally {
      activeLoggers.decrementAndGet()
      loggers.remove()
      logger.close()
    }
  }

  def loggerOutputPath: Path = {
    val temp = Paths.get(System.getProperty("java.io.tmpdir"))
    temp.resolve("intellij-scala-trace-logger")
  }

  private def createTraceLogWriter(topic: String): TraceLogWriter = this.synchronized {
    val thread = Thread.currentThread()
    val dir = loggerOutputPath
    val ext = ".log"
    val initialName =
      if (topic.isEmpty) s"thread${thread.getId}"
      else s"$topic-thread${thread.getId}"

    @tailrec
    def findFreePath(name: String, attempt: Int): Path = {
      val path = dir.resolve(name + ext)
      if (!Files.exists(path)) path
      else findFreePath(s"$initialName.$attempt", attempt + 1)
    }

    val path = findFreePath(initialName, 1)
    if (!Files.exists(dir)) {
      Files.createDirectories(dir)
    }
    val writer = new FileWriter(path.toFile)

    new FileWritingTraceLogger(writer)
  }

  private object NoOpLogger extends TraceLogWriter {
    override def log(msg: String, values: Seq[ValueDesc], st: StackTrace): Unit = ()
    override def startEnclosing(msg: String, args: Seq[ValueDesc], st: StackTrace): Unit = ()
    override def enclosingSuccess(result: Data, st: StackTrace): Unit = ()
    override def enclosingFail(exception: Throwable, st: StackTrace): Unit = ()
  }

  class FileWritingTraceLogger(writer: java.io.Writer) extends TraceLogWriter {
    private[this] var prevCallStack: StackTrace = Array.empty

    /**
     * This method updates prevCallStack and calculates the difference between
     * the new callstack and the previous callstack, so that we only have to
     * write the difference.
     */
    private[this] def updatePrevCallStack(st: StackTrace): StackTraceDiff = {
      // the
      val commonLength = math.min(prevCallStack.length, st.length)
      var same = 0
      while (same < commonLength && st(st.length - same - 1) == prevCallStack(prevCallStack.length - same - 1)) {
        same += 1
      }
      prevCallStack = st

      // we have to throw the first entry away, because it is the frame in Thread::stackTrace() itself
      val newEntries = st.iterator
        .slice(1, st.length - same)
        .map(StackTraceEntry.from)
        .toSeq

      StackTraceDiff(same, newEntries)
    }

    private[this] def write(msg: TraceLoggerEntry): Unit = {
      SerializationApi.writeTo(msg, writer)
      writer.append('\n')
      writer.flush()
    }

    override def log(msg: String, values: Seq[ValueDesc], st: StackTrace): Unit =
      write(TraceLoggerEntry.Msg(msg, values, updatePrevCallStack(st)))
    override def startEnclosing(msg: String, args: Seq[ValueDesc], st: StackTrace): Unit =
      write(TraceLoggerEntry.Start(msg, args, updatePrevCallStack(st)))
    override def enclosingSuccess(result: Data, st: StackTrace): Unit = {
      prevCallStack = st
      write(TraceLoggerEntry.Success(result))
    }

    override def enclosingFail(exception: Throwable, st: StackTrace): Unit = {
      prevCallStack = st
      write(TraceLoggerEntry.Fail(exception.getMessage))
    }

    override def close(): Unit = writer.close()
  }
}
