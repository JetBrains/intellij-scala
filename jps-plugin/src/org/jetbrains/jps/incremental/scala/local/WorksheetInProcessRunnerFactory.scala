package org.jetbrains.jps.incremental.scala.local

import java.io.{File, OutputStream, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URL}
import java.nio.ByteBuffer

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.ThreadLocalPrintStream
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.remote.{EventGeneratingClient, Arguments, WorksheetOutputEvent}

/**
 * User: Dmitry.Naydanov
 * Date: 03.12.14.
 */
class WorksheetInProcessRunnerFactory {
  trait WorksheetInProcessRunner {
    def loadAndRun(arguments: Arguments, client: EventGeneratingClient)
  }

  def getRunner(out: PrintStream, standalone: Boolean): WorksheetInProcessRunner = new WorksheetInProcessRunnerImpl(out, standalone)

  private var classLoader: Option[(Set[URL], Set[URL], URLClassLoader)] = None

  private def createClassLoader(compilerUrls: Set[URL], classpathUrls: Set[URL]) = {
    val loader = new URLClassLoader((compilerUrls ++ classpathUrls).toArray, null)
    classLoader = Some((compilerUrls, classpathUrls, loader))
    loader
  }

  private def getClassLoader(compilerUrls: Seq[URL], classpathUrls: Seq[URL]) = {
    val compilerSet = compilerUrls.toSet
    val classpathSet = classpathUrls.toSet

    classLoader match {
      case Some((urls1, urls2, loader)) =>
        if (compilerSet == urls1 && classpathSet == urls2) loader else createClassLoader(compilerSet, classpathSet)
      case _ => createClassLoader(compilerSet, classpathSet)
    }
  }

  private class WorksheetInProcessRunnerImpl(out: PrintStream, standalone: Boolean) extends WorksheetInProcessRunner {
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

          val worksheetUrls = arguments.worksheetFiles.tail.map(toUrlSpec)
          val compilerUrlSeq = compilerUrls.map(toUrlSpec)
          val classpathUrls = arguments.compilationData.classpath.map(_.toURI.toURL)

          val classLoader = new URLClassLoader(worksheetUrls.toArray, getClassLoader(compilerUrlSeq, classpathUrls diff worksheetUrls.map(_.toURI.toURL) ))

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
