package org.jetbrains.plugins.scala.compiler

import com.intellij.execution.process.{BaseProcessHandler, ProcessHandler}

import java.io.{BufferedReader, InputStreamReader, PrintStream}
import scala.util.{Failure, Success, Try}

object ScalaExecutionTestUtils {

  def printThreadDumpAfterTimeout(
    handler: ProcessHandler,
    out: PrintStream = System.err,
  ): Unit = {
    out.println("Process is still running after timeout. Creating thread dump...")

    Try {
      val process = handler.asInstanceOf[BaseProcessHandler[java.lang.Process]].getProcess
      val pid = process.pid()
      out.println(s"Process ID: $pid")
      out.println(s"\n=== Thread Dump for PID $pid ===")

      ProcessHandle.of(pid).ifPresent { handle =>
        handle.info().command().ifPresent(cmd => out.println(s"Command: $cmd"))
        handle.info().arguments().ifPresent(args => out.println(s"Arguments: ${args.mkString(" ")}"))
      }

      // Try jstack first, fall back to jcmd
      val threadDumpResult = run_jstack(pid).orElse(run_jcmd(pid))

      threadDumpResult match {
        case Success(dump) =>
          out.println(dump)
        case Failure(ex) =>
          out.println(
            s"""Failed to obtain thread dump: ${ex.getMessage}
               |Ensure JDK tools (jstack or jcmd) are in PATH.""".stripMargin
          )
      }
    }.recover {
      case ex: Exception =>
        out.println(s"Failed to create thread dump: ${ex.getMessage}")
        ex.printStackTrace(out)
    }
  }

  private lazy val isWindows: Boolean = {
    val osName = System.getProperty("os.name").toLowerCase
    osName.contains("win")
  }

  private def run_jstack(pid: Long): Try[String] = {
    val jstackCmd = if (isWindows) "jstack.exe" else "jstack"
    runCommand(jstackCmd, pid.toString)
  }

  private def run_jcmd(pid: Long): Try[String] = {
    val jcmdCmd = if (isWindows) "jcmd.exe" else "jcmd"
    runCommand(jcmdCmd, pid.toString, "Thread.print")
  }

  private def runCommand(command: String*): Try[String] = Try {
    val process = new ProcessBuilder(command: _*)
      .redirectErrorStream(true)
      .start()

    val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val output = Iterator.continually(reader.readLine()).takeWhile(_ != null).mkString("\n")
    process.waitFor()
    output
  }
}
