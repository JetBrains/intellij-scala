package org.jetbrains.plugins.scala.traceLogger

import junit.framework.TestCase
import org.jetbrains.plugins.scala.traceLogger.TraceLoggerTestBase.WriteToStringLogger
import org.junit.Assert

import scala.runtime.NonLocalReturnControl

abstract class TraceLoggerTestBase extends TestCase {
  class TestException(msg: String) extends Exception(msg)
  
  def doTest(expectedLog: String)(body: => Unit): Unit = {
    val logger = new WriteToStringLogger
    try {
      TraceLogger.runWithTraceLogger("run-test", _ => logger)(body)
    } catch {
      case e: NonLocalReturnControl[_] =>
        throw new AssertionError("Test used return to jump out of testing method!", e)
      case _: TestException =>
        // catch and continue as if nothing had happened
    } finally {
      Assert.assertEquals(expectedLog.trim, logger.result.trim)
    }
  }
}

object TraceLoggerTestBase {
  class WriteToStringLogger extends TraceLogger {
    private val buffer = new StringBuilder
    private var indent = 0

    private def write(msg: String, values: Seq[(String, Data)] = Seq.empty): Unit = {
      buffer ++= "  " * indent
      buffer ++= msg

      if (values.nonEmpty) {
        buffer ++= " ("
        buffer ++= values.map { case (name, data) => s"$name = $data" }.mkString(", ")
        buffer += ')'
      }

      buffer ++= System.lineSeparator()
    }

    override def log(msg: String, values: Seq[(String, Data)], st: StackTrace): Unit =
      write(msg, values)

    override def startEnclosing(msg: String, args: Seq[(String, Data)], st: StackTrace): Unit = {
      val txt =
        if (msg == null) st.tail.head.getMethodName
        else msg
      write(txt, args)
      indent += 1
    }

    override def enclosingSuccess(result: Data, st: StackTrace): Unit = {
      write(s"-> $result")
      indent -= 1
    }

    override def enclosingFail(exception: Throwable, st: StackTrace): Unit = {
      write(s"-> threw [${exception.getMessage}]")
      indent -= 1
    }

    def result: String = buffer.result()
  }
}
