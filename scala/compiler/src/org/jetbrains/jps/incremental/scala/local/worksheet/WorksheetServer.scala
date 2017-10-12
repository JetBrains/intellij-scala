package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io._
import java.net.URL
import java.nio.ByteBuffer

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.ThreadLocalPrintStream
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.local.worksheet.compatibility.WorksheetArgsJava
import org.jetbrains.jps.incremental.scala.remote.{Arguments, EventGeneratingClient, WorksheetOutputEvent}

import scala.collection.JavaConverters._

/**
  * User: Dmitry.Naydanov
  * Date: 01.02.17.
  */
class WorksheetServer {
  import WorksheetServer._

  private val plainFactory = new WorksheetInProcessRunnerFactory
  private val replFactory = new ILoopWrapperFactoryHandler


  def loadAndRun(commonArguments: Arguments, out: PrintStream, client: EventGeneratingClient, standalone: Boolean) {
    val printStream = new MyEncodingOutputStream(out, standalone)
    
    if (isRepl(commonArguments)) replFactory.loadReplWrapperAndRun(commonArguments, printStream, Option(client)) else 
      WorksheetServer.parseWorksheetArgsFrom(commonArguments) foreach {
        args => plainFactory.getRunner(printStream, standalone).loadAndRun(args, client)
      }
  }

  def isRepl(commonArguments: Arguments): Boolean = 
    commonArguments.worksheetFiles.lastOption.contains("replenabled")
}

object WorksheetServer {
  def patchSystemOut(out: OutputStream) {
    val printStream = new PrintStream(out)
    
    System.out match {
      case threadLocal: ThreadLocalPrintStream => threadLocal.init(printStream)
      case _ => System.setOut(printStream)
    }
  }

  def convertWorksheetArgsFromJava(javaArgs: WorksheetArgsJava): WorksheetArgs = {
    val replArgs = Option(javaArgs.getReplArgs) map (ra => ReplArgs(ra.getSessionId, ra.getCodeChunk))
    
    WorksheetArgs(
      javaArgs.getWorksheetClassName, javaArgs.getPathToRunners, javaArgs.getWorksheetTempFile, 
      javaArgs.getOutputDirs.asScala, replArgs, javaArgs.getNameForST, 
      CompilerJars(javaArgs.getCompLibrary, javaArgs.getCompiler, javaArgs.getCompExtra.asScala), javaArgs.getClasspathURLs.asScala
    )
  }
  
  def parseWorksheetArgsFrom(commonArgs: Arguments): Option[WorksheetArgs] = {
    val compilerJars = commonArgs.compilerData.compilerJars.orNull
    
    if (compilerJars == null) return None
    
    val javaArgs = WorksheetArgsJava.constructArgsFrom(
      commonArgs.worksheetFiles.asJava, 
      commonArgs.compilationData.sources.headOption.map(_.getName).orNull, 
      compilerJars.library, compilerJars.compiler, compilerJars.extra.asJava, 
      commonArgs.compilationData.classpath.asJava
    )
    
    Option(javaArgs) map convertWorksheetArgsFromJava
  }
  
  case class WorksheetArgs(compiledClassName: String, pathToRunners: File, worksheetTemp: File, outputDirs: Seq[File], 
                           replArgs: Option[ReplArgs], nameForST: String, compilerJars: CompilerJars, classpathUrls: Seq[URL])

  case class ReplArgs(sessionId: String, codeChunk: String)
  
  class MyEncodingOutputStream(delegateOut: PrintStream, standalone: Boolean) extends OutputStream {
    private var capacity = 1200
    private var buffer = ByteBuffer.allocate(capacity)

    override def write(b: Int) {
      if (b == '\r') return

      if (buffer.position() < capacity) buffer.put(b.toByte) else {
        val copy = buffer.array().clone()
        capacity *= 2
        buffer = ByteBuffer.allocate(capacity)
        buffer put copy
        buffer put b.toByte
      }

      if (b == '\n') flush()
    }
    
    override def close() {
      flush()
    }

    override def flush() {
      if (buffer.position() == 0) return
      val event = WorksheetOutputEvent(new String(buffer.array(), 0, buffer.position()))
      buffer.clear()
      val encode = Base64Converter.encode(event.toBytes)
      delegateOut.write(if (standalone && !encode.endsWith("=")) (encode + "=").getBytes else encode.getBytes)
    }
  }
  
  class MyUpdatePrintWriter(stream: OutputStream) extends PrintWriter(stream) {
    private var curHash = stream.hashCode()
    
    def updateOut(stream: OutputStream) {
      if (stream.hashCode() != curHash) {
        out = new BufferedWriter(new OutputStreamWriter(stream))
        curHash = stream.hashCode()
      }
    }
  }
}
