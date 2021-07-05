package org.jetbrains.jps.incremental.scala

object ClientUtils {

  def reportException(message: String, ex: Throwable, client: Client): Unit = {
    val tid = Thread.currentThread.getId
    client.internalInfo(s"[t$tid] $message\n${exceptionText(ex)}")
  }

  private def exceptionText(ex: Throwable): String =
    s"${ex.toString}\n${stackTraceText(ex)}"

  private def stackTraceText(exception: Throwable): String =
    stackTraceText(exception.getStackTrace)

  private def stackTraceText(stackTrace: Array[StackTraceElement]): String = {
    val linePrefix = "\tat "
    stackTrace.mkString(linePrefix, "\n" + linePrefix, "")
  }
}
