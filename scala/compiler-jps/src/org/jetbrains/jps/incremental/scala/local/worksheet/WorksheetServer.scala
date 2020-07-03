package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io._
import java.nio.{Buffer, ByteBuffer}

import org.jetbrains.jps.incremental.scala.{Client, JpsBundle}
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactoryHandler.ReplContext
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilerJars}
import org.jetbrains.plugins.scala.compiler.data.worksheet._

class WorksheetServer {
  import WorksheetServer._

  private val plainFactory = new WorksheetInProcessRunnerFactory
  private val replFactoryHandler = new ILoopWrapperFactoryHandler

  def loadAndRun(
    worksheetArgs: WorksheetArgs,
    commonArgs: Arguments,
    client: Client
  ): Unit = {
    def printStream = new PrintStream(new RedirectToClientOutputStream(client))

    val compilerJars = commonArgs.compilerData.compilerJars.getOrElse {
      client.error(JpsBundle.message("compiler.jars.are.missing"))
      return
    }
    worksheetArgs match {
      case args: WorksheetArgsRepl  =>
        val context = replContext(commonArgs, compilerJars)
        replFactoryHandler.loadReplWrapperAndRun(args, context, printStream, client)
      case args: WorksheetArgsPlain =>
        val context = plainContext(commonArgs, compilerJars)
        plainFactory.getRunner(printStream).loadAndRun(args, context, client)
    }
  }

  private def replContext(args: Arguments, compilerJars: CompilerJars): ReplContext = {
    val compilationData = args.compilationData
    ReplContext(
      args.sbtData,
      compilerJars,
      compilationData.classpath,
      compilationData.scalaOptions
    )
  }

  private def plainContext(args: Arguments, compilerJars: CompilerJars): WorksheetRunnerContext =
    WorksheetRunnerContext(
      compilerJars,
      args.compilationData.classpath
    )
}

object WorksheetServer {

  private class RedirectToClientOutputStream(client: Client) extends OutputStream {
    private var capacity = 1200
    private var buffer = ByteBuffer.allocate(capacity)

    override def write(b: Int): Unit = {
      if (b == '\r') return

      if (buffer.position() >= capacity)
        growBuffer()
      buffer.put(b.toByte)

      if (b == '\n')
        flush()
    }

    private def growBuffer(): Unit = {
      capacity *= 2
      val newBuffer = ByteBuffer.allocate(capacity)
      newBuffer.put(buffer.array())
      val old = buffer
      buffer = newBuffer
      clearBuffer(old)
    }

    override def close(): Unit =
      flush()

    override def flush(): Unit = {
      if (buffer.position() == 0) return

      val worksheetOutputText = new String(buffer.array(), 0, buffer.position())
      client.worksheetOutput(worksheetOutputText)

      clearBuffer(buffer)
    }
  }

  // ATTENTION: use this method to clear buffer, do not delete this to Buffer!
  // it is required to be run on JDK 8 in case plugin is built with JDK 11, see SCL-16277 for the details
  private def clearBuffer(buffer: Buffer): Unit = {
    buffer.clear()
  }
}
