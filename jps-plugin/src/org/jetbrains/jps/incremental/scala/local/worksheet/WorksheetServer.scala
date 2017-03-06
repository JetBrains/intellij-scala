package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io._
import java.net.URL
import java.nio.ByteBuffer

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.ThreadLocalPrintStream
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.remote.{Arguments, EventGeneratingClient, WorksheetOutputEvent}

/**
  * User: Dmitry.Naydanov
  * Date: 01.02.17.
  */
class WorksheetServer {
  import WorksheetServer._

  private val plainFactory = new WorksheetInProcessRunnerFactory
  private val replFactory = new ILoopWrapperFactory


  def loadAndRun(commonArguments: Arguments, out: PrintStream, client: EventGeneratingClient, standalone: Boolean) {
    parseWorksheetArgs(commonArguments.worksheetFiles.toArray, commonArguments) foreach {
      args =>
        val printStream = new MyEncodingOutputStream(out, standalone)
        
        args.replArgs match {
          case Some(_) =>
            patchSystemOut(printStream)
            replFactory.loadReplWrapperAndRun(commonArguments.sbtData, args, printStream, Option(client))
          case _ => 
            plainFactory.getRunner(printStream, standalone).loadAndRun(args, client)
        }
    }
  }

  def isRepl(commonArguments: Arguments): Boolean = 
    commonArguments.worksheetFiles.lastOption.contains("replenabled")
  
  private def parseWorksheetArgs(argsString: Array[String], args: Arguments): Option[WorksheetArgs] = {
    @inline def error(msg: String) = throw new IllegalArgumentException(msg)

    def pathToFile(path: String): Option[File] = Option(path).map {
      p => new File(p)
    } filter(_.exists())
    
    def validate(fileOpt: Option[File], argName: String): File = {
      fileOpt match {
        case Some(file) => if (file.exists()) file else error(s"$argName with value ${file.getAbsolutePath} doesn't exist")
        case _ => error(s"$argName is null")
      }
    }

    val replArgs = argsString.lastOption match {
      case Some("replenabled") =>
        if (argsString.length < 7) error(s"Invalid arg count: expected at least 7, but got ${argsString.length}")
        Some(ReplArgs(argsString(argsString.length - 3), argsString(argsString.length - 2)))
      case Some(_) =>
        if (argsString.length < 4) error(s"Invalid arg count: expected at least 4, but got ${argsString.length}")
        None
      case None => return None
    }
    
    Some(WorksheetArgs(
      compiledClassName = argsString(0),
      pathToRunners = validate(pathToFile(argsString(1)), "pathToRunners"),
      worksheetTemp = validate(pathToFile(argsString(2)), "worksheetTempFile"),
      outputDirs = argsString.slice(3, argsString.length - 3).flatMap(path => pathToFile(path)),
      replArgs,
      args.compilationData.sources.headOption.orNull.getName,
      args.compilerData.compilerJars.getOrElse(error("Cannot find compiler jars")),
      args.compilationData.classpath.map(_.toURI.toURL)
    ))
  }
}

object WorksheetServer {
  def patchSystemOut(out: OutputStream) {
    val printStream = new PrintStream(out)
    
    System.out match {
      case threadLocal: ThreadLocalPrintStream => threadLocal.init(printStream)
      case _ => System.setOut(printStream)
    }
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
