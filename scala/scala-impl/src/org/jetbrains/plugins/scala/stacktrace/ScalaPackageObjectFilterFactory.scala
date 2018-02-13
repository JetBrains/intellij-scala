package org.jetbrains.plugins.scala.stacktrace

import java.util.regex.Pattern

import scala.collection.JavaConverters._

import com.intellij.execution.filters._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
  * Nikolay.Tropin
  * 09-Nov-17
  */
class ScalaPackageObjectFilterFactory extends ExceptionFilterFactory {
  override def create(scope: GlobalSearchScope): Filter =
    new ScalaPackageObjectFilter(scope)
}

object ScalaPackageObjectFilter {
  //from kotlin exception filter
  private val STACK_TRACE_ELEMENT_PATTERN =
    Pattern.compile("^[\\w|\\s]*at\\s+(.+)\\.(.+)\\((.+):(\\d+)\\)\\s*$")

  private def parseStackTraceLine(line: String): StackTraceElement = {
    val matcher = STACK_TRACE_ELEMENT_PATTERN.matcher(line)
    if (matcher.matches()) {
      val declaringClass = matcher.group(1)
      val methodName = matcher.group(2)
      val fileName = matcher.group(3)
      val lineNumber = matcher.group(4)
      new StackTraceElement(declaringClass, methodName, fileName, Integer.parseInt(lineNumber))
    }
    else null
  }
}

class ScalaPackageObjectFilter(scope: GlobalSearchScope) extends ExceptionFilter(scope) {
  import ScalaPackageObjectFilter.parseStackTraceLine

  override def applyFilter(line: String, textEndOffset: Int): Filter.Result = {
    line match {
      case packageObjectFile(vFile, lineNumber) =>
        val link = new OpenFileHyperlinkInfo(scope.getProject, vFile, lineNumber)
        val defaultResult = new ExceptionFilter(scope).applyFilter(line, textEndOffset)

        val updated =
          defaultResult.getResultItems.asScala
            .map(updateLink(_, link)).asJava
        new Filter.Result(updated)
      case _ => null
    }
  }

  private object packageObjectFile {
    def unapply(line: String): Option[(VirtualFile, Int)] = {
      val project = scope.getProject

      val stackTraceElement = parseStackTraceLine(line)
      if (stackTraceElement == null || project == null)
        return None

      val fileName = stackTraceElement.getFileName.toLowerCase
      if (!fileName.endsWith(".scala"))
        return None

      val className = stackTraceElement.getClassName
      if (className.isEmpty || !className.contains("package$"))
        return None

      val packageName = className.split('.').dropRight(1).mkString(".")
      val obj = ScalaShortNamesCacheManager.getInstance(project).getPackageObjectByName(packageName, scope)
      for {
        file <- obj.containingFile
        vFile <- Option(file.getVirtualFile)
      } yield (vFile, stackTraceElement.getLineNumber)
    }
  }

  private def updateLink(item: Filter.ResultItem, link: HyperlinkInfo): Filter.ResultItem = {
    new Filter.ResultItem(item.getHighlightStartOffset, item.getHighlightEndOffset, link, item.getHighlightAttributes)
  }
}