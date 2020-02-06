package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io._
import java.net.URL
import java.nio.{Buffer, ByteBuffer}

import com.martiansoftware.nailgun.ThreadLocalPrintStream
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.worksheet.compatibility.{ReplArgsJava, WorksheetArgsJava}
import org.jetbrains.plugins.scala.compiler.data
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilerJars}

import scala.collection.JavaConverters._

class WorksheetServer {
  import WorksheetServer._

  private val plainFactory = new WorksheetInProcessRunnerFactory
  private val replFactory = new ILoopWrapperFactoryHandler

  def loadAndRun(
    commonArguments: Arguments,
    client: Client
  ): Unit = {
    val outputStream = new RedirectToClientOutputStream(client)
    val printStream = new PrintStream(outputStream)
    
    if (isRepl(commonArguments)) {
      replFactory.loadReplWrapperAndRun(commonArguments, printStream, client)
    } else {
      val argsParsed = WorksheetServer.parseWorksheetArgsFrom(commonArguments)
      argsParsed.foreach { args =>
        plainFactory.getRunner(printStream).loadAndRun(args, client)
      }
    }
  }

  def isRepl(commonArguments: Arguments): Boolean = 
    commonArguments.worksheetFiles.lastOption.contains("replenabled")
}

object WorksheetServer {

  def patchSystemOut(out: OutputStream): Unit = {
    val printStream = new PrintStream(out)

    System.out match {
      case threadLocal: ThreadLocalPrintStream => threadLocal.init(printStream)
      case _ => System.setOut(printStream)
    }
  }

  def convertWorksheetArgsFromJava(javaArgs: WorksheetArgsJava): WorksheetArgs = {
    val replArgs = Option(javaArgs.getReplArgs).map(ReplArgs.fromJava)

    val compilerJars = data.CompilerJars(
      javaArgs.getCompLibrary,
      javaArgs.getCompiler,
      javaArgs.getCompExtra.asScala
    )
    WorksheetArgs(
      javaArgs.getWorksheetClassName,
      javaArgs.getPathToRunners,
      javaArgs.getWorksheetTempFile,
      javaArgs.getOutputDirs.asScala,
      replArgs,
      javaArgs.getNameForST,
      compilerJars,
      javaArgs.getClasspathURLs.asScala
    )
  }
  
  def parseWorksheetArgsFrom(commonArgs: Arguments): Option[WorksheetArgs] = {
    val compilerJars = commonArgs.compilerData.compilerJars.orNull
    
    if (compilerJars == null) return None
    
    val javaArgs = WorksheetArgsJava.constructArgsFrom(
      commonArgs.worksheetFiles.asJava,
      commonArgs.compilationData.scalaOptions.asJava,
      commonArgs.compilationData.sources.headOption.map(_.getName).orNull, 
      compilerJars.library,
      compilerJars.compiler,
      compilerJars.extra.asJava,
      commonArgs.compilationData.classpath.asJava
    )
    
    Option(javaArgs).map(convertWorksheetArgsFromJava)
  }
  
  case class WorksheetArgs(compiledClassName: String,
                           pathToRunners: File,
                           worksheetTemp: File,
                           outputDirs: Seq[File],
                           replArgs: Option[ReplArgs],
                           nameForST: String,
                           compilerJars: CompilerJars,
                           classpathUrls: Seq[URL])

  case class ReplArgs(sessionId: String, codeChunk: String)

  object ReplArgs {
    def fromJava(args: ReplArgsJava): ReplArgs = ReplArgs(args.getSessionId, args.getCodeChunk)
  }

  private class RedirectToClientOutputStream(client: Client) extends OutputStream {
    private var capacity = 1200
    private var buffer = ByteBuffer.allocate(capacity)

    override def write(b: Int): Unit = {
      if (b == '\r') return

      if (buffer.position() >= capacity)
        grow()
      buffer.put(b.toByte)

      if (b == '\n')
        flush()
    }

    private def grow(): Unit = {
      capacity *= 2
      val newBuffer = ByteBuffer.allocate(capacity)
      newBuffer.put(buffer.array())
      val old = buffer
      buffer = newBuffer
      old.clear()
    }

    override def close(): Unit =
      flush()

    override def flush(): Unit = {
      if (buffer.position() == 0) return
      val worksheetOutputText = new String(buffer.array(), 0, buffer.position())
      client.worksheetOutput(worksheetOutputText)

      // ATTENTION: do not delete this cast to Buffer!
      // it is required to be run on JDK 8 in case plugin is built with JDK 11, see SCL-16277 for the details
      buffer.asInstanceOf[Buffer].clear()
    }
  }

  // buffering is already done in MyEncodingOutputStream
  class MyUpdatePrintStream(stream: OutputStream) extends PrintStream(stream) {
    private var curHash = stream.hashCode

    def updateOut(stream: OutputStream): Unit = {
      if (stream.hashCode != curHash) {
        out = stream
        curHash = stream.hashCode()
      }
    }
  }

  class MyUpdatePrintWriter(stream: OutputStream) extends PrintWriter(stream) {
    private var curHash = stream.hashCode()

    def updateOut(stream: OutputStream): Unit = {
      if (stream.hashCode() != curHash) {
        out = new BufferedWriter(new OutputStreamWriter(stream))
        curHash = stream.hashCode()
      }
    }
  }
}
