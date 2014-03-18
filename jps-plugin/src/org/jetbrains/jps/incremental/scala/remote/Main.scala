package org.jetbrains.jps.incremental.scala
package remote

import local.LocalServer
import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.{ThreadLocalPrintStream, NGContext}
import java.io.{OutputStream, PrintStream, File}
import java.net.URLClassLoader
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import java.nio.ByteBuffer
import java.lang.reflect.InvocationTargetException

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov         
 */
object Main {
  private val Server = new LocalServer()

  def nailMain(context: NGContext) {
    make(context.getArgs.toSeq, context.out, false)
  }
  
  def main(args: Array[String]) {
    make(args, System.out, true)
  }
  
  private def make(arguments: Seq[String], out: PrintStream, standalone: Boolean) {
    var hasErrors = false

    val client = {
      val eventHandler = (event: Event) => {
        val encode = Base64Converter.encode(event.toBytes)
        out.write((if (standalone && !encode.endsWith("=")) encode + "=" else encode).getBytes)
      }
      new EventGeneratingClient(eventHandler, out.checkError) {
        override def error(text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
          hasErrors = true
          super.error(text, source, line, column)
        }
      }
    }

    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)
    
    try {
      val args = {
        val strings = arguments.map {
          arg => 
            val s = new String(Base64Converter.decode(arg.getBytes), "UTF-8")
            if (s == "#STUB#") "" else s
        }
        Arguments.from(strings)
      }
      
      Server.compile(args.sbtData, args.compilerData, args.compilationData, client)

      if (!hasErrors) new WorksheetInProcessRunner(out, standalone).loadAndRun(args, client)
    } catch {
      case e: Throwable => 
        client.trace(e)
    } finally {
      System.setOut(oldOut)
    }
  }
  
  
  private class WorksheetInProcessRunner(out: PrintStream, standalone: Boolean) {
    private val TRACE_PREFIX = 21
    private val WORKSHEET = "#worksheet#"
    
    private val myOut = new OutputStream {
      private var capacity = 1200
      private var buffer = ByteBuffer.allocate(capacity)

      override def write(b: Int) {
        if (b == '\r') return

        if (buffer.position() < capacity) buffer.put(b.toByte) else {
          val copy = buffer.array().clone()
          capacity *= 2
          buffer = ByteBuffer.allocate(capacity)
          buffer.put(copy)
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
        out.write(if (standalone && !encode.endsWith("=")) (encode + "=").getBytes else encode.getBytes)
      }
    }
    
    def loadAndRun(arguments: Arguments, client: EventGeneratingClient) {
      arguments.worksheetFiles.headOption.map {
        case className =>
          def toUrlSpec(p: String) = new File(p).toURI.toURL

          val compilerUrls = arguments.compilerData.compilerJars map {
            case CompilerJars(lib, compiler, extra) => Seq(lib, compiler) ++ extra
          } map (a => a.map(_.getCanonicalPath)) getOrElse Seq.empty

          val urls = 
            arguments.worksheetFiles.tail.map(toUrlSpec) ++ 
            compilerUrls.map(toUrlSpec) ++ 
            arguments.compilationData.classpath.map(_.toURI.toURL)
          val classLoader = new URLClassLoader(urls.toArray)

          try {
            val cl = Class.forName(className, true, classLoader)

            cl.getDeclaredMethods.find {
              case m => m.getName == "main"
            } map {
              case method =>
                System.out match {
                  case threadLocal: ThreadLocalPrintStream => threadLocal.init(new PrintStream(myOut))
                  case _ => System.setOut(new PrintStream(myOut))
                }
                method.invoke(null, null)
            }
          } catch {
            case userEx: InvocationTargetException => 
              myOut.flush()
              
              val e = if (userEx.getCause != null) userEx.getCause else userEx
              cleanStackTrace(
                e, arguments.compilationData.sources.headOption.orNull.getName, className + "$" + className
              ).printStackTrace(new PrintStream(myOut, false))
            case e: Exception =>
              client trace e
          } finally {
            myOut.flush()
          }
      }
    }

    private def cleanStackTrace(e: Throwable, fileName: String, className: String): Throwable = {
      def transformElement(original: StackTraceElement): StackTraceElement = {
        val originalClassName = original.getClassName
        val declaringClassName = 
          if (originalClassName == className) WORKSHEET else 
          if (originalClassName.startsWith(className + "$")) 
            WORKSHEET + "." + originalClassName.substring(className.length + 1) else originalClassName
        val originalFileName = if (fileName == null) original.getFileName else fileName
        new StackTraceElement(declaringClassName, original.getMethodName, originalFileName, original.getLineNumber - 4)
      }

      val els = e.getStackTrace
      val length = els.length
      if (length < TRACE_PREFIX) return e
      
      val newTrace = new Array[StackTraceElement](length - TRACE_PREFIX + 1)
      val referenceElement = els(length - TRACE_PREFIX)

      newTrace(newTrace.length - 1) = 
        new StackTraceElement(WORKSHEET, WORKSHEET, if (fileName == null) referenceElement.getFileName else fileName, referenceElement.getLineNumber - 4)

      var i: Int = 0
      while (i < newTrace.length - 1) {
        newTrace(i) = transformElement(els(i))
        i += 1
      }

      e setStackTrace newTrace
      e
    }
  }
}
