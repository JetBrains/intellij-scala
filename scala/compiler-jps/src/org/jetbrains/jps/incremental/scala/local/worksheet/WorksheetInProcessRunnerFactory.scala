package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, OutputStream, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URL, URLClassLoader}

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.worksheet.util.IOUtils
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgsPlain

class WorksheetInProcessRunnerFactory {

  def getRunner(out: OutputStream): WorksheetInProcessRunner = new WorksheetInProcessRunnerImpl(out)

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
      case Some((urls1, urls2, loader)) if compilerSet == urls1 && classpathSet == urls2 =>
        loader
      case _ =>
        createClassLoader(compilerSet, classpathSet)
    }
  }

  private class WorksheetInProcessRunnerImpl(out: OutputStream) extends WorksheetInProcessRunner {
    private val TRACE_PREFIX = 21
    private val WORKSHEET = "#worksheet#"

    override def loadAndRun(args: WorksheetArgsPlain, context: WorksheetRunnerContext, client: Client): Unit = {
      def toUrlSpec(p: String): URL = new File(p).toURI.toURL

      val classLoader: URLClassLoader = {
        val worksheetUrls = (Seq(args.pathToRunnersJar, args.worksheetTempFile) ++ args.outputDirs).map(_.toURI.toURL)
        val classpathUrls = context.classpath.map(_.toURI.toURL)
        val compilerUrls  = {
          val jars = context.compilerJars.allJars
          jars.map(_.getCanonicalPath).map(toUrlSpec)
        }

        val parent = getClassLoader(compilerUrls, classpathUrls.diff(worksheetUrls))
        new URLClassLoader(worksheetUrls.toArray, parent)
      }

      val className = args.worksheetClassName
      try {
        val cl = Class.forName(className, true, classLoader)

        cl.getDeclaredMethods.find(_.getName == "main").map { method =>
          IOUtils.patchSystemOut(out)
          method.invoke(null, new PrintStream(out))
        }
      } catch {
        case userEx: InvocationTargetException =>
          out.flush()

          val ex = if (userEx.getCause != null) userEx.getCause else userEx
          val exClean = cleanStackTrace(ex, args.originalFileName, className + "$" + className)
          exClean.printStackTrace(new PrintStream(out, false))
        case e: Exception =>
          client.trace(e)
      } finally {
        out.flush()
      }
    }

    private def cleanStackTrace(e: Throwable, originalFileName: String, className: String): Throwable = {

      def transformElement(original: StackTraceElement): StackTraceElement = {
        val originalClassName = original.getClassName
        val declaringClassName =
          if (originalClassName == className) WORKSHEET
          else if (originalClassName.startsWith(className + "$"))
            WORKSHEET + "." + originalClassName.substring(className.length + 1)
          else originalClassName
        new StackTraceElement(declaringClassName, original.getMethodName, originalFileName, original.getLineNumber - 4)
      }

      val els = e.getStackTrace
      val length = els.length
      if (length < TRACE_PREFIX) return e

      val newTrace = new Array[StackTraceElement](length - TRACE_PREFIX + 1)
      val referenceElement = els(length - TRACE_PREFIX)

      newTrace(newTrace.length - 1) = {
        val fileNameFinal = originalFileName
        val newElement = new StackTraceElement(WORKSHEET, WORKSHEET, fileNameFinal, referenceElement.getLineNumber - 4)
        newElement
      }

      var idx: Int = 0
      while (idx < newTrace.length - 1) {
        newTrace(idx) = transformElement(els(idx))
        idx += 1
      }

      e.setStackTrace(newTrace)
      e
    }
  }
}
