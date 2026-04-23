package org.jetbrains.plugins.scala.stacktrace

import com.intellij.execution.filters._
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.PackageObjectSingletonClassName

import scala.jdk.CollectionConverters._

class ScalaPackageObjectFilterFactory extends ExceptionFilterFactory {
  override def create(scope: GlobalSearchScope): Filter =
    new ScalaPackageObjectFilter(scope)
}

class ScalaPackageObjectConsoleFilterProvider extends ConsoleFilterProvider {
  override def getDefaultFilters(project: Project): Array[Filter] =
    Array(new ScalaPackageObjectFilter(GlobalSearchScope.allScope(project)))
}

private object ScalaPackageObjectFilter {
  // Based on org.jetbrains.kotlin.idea.debugger.core.KotlinExceptionFilter
  private def parseStackTraceLine(line: String): StackTraceElement = ExceptionWorker.parseExceptionLine(line) match {
    case null => null
    case parsed =>
      val declaringClass = parsed.classFqnRange.substring(line)
      val methodName = parsed.methodNameRange.substring(line)
      val fileName = parsed.fileName
      val lineNumber = parsed.lineNumber
      new StackTraceElement(declaringClass, methodName, fileName, lineNumber)
  }
}

class ScalaPackageObjectFilter(scope: GlobalSearchScope) extends ExceptionFilter(scope) {
  import ScalaPackageObjectFilter.parseStackTraceLine

  override def applyFilter(line: String, textEndOffset: Int): Filter.Result = {
    inReadAction {
      line match {
        case packageObjectFile(vFile, lineNumber) =>
          val link = new OpenFileHyperlinkInfo(scope.getProject, vFile, lineNumber)
          val defaultResult = new ExceptionFilter(scope).applyFilter(line, textEndOffset)

          if (defaultResult == null)
            return null


          val updated =
            defaultResult.getResultItems.asScala
              .map(updateLink(_, link)).asJava
          new Filter.Result(updated)
        case _ => null
      }
    }
  }

  private object packageObjectFile {
    def unapply(line: String): Option[(VirtualFile, Int)] = {
      val project = scope.getProject

      val stackTraceElement = parseStackTraceLine(line)
      if (stackTraceElement == null || project == null)
        return None

      val fileName = stackTraceElement.getFileName
      if (fileName == null || !fileName.toLowerCase.endsWith(".scala"))
        return None

      val className = stackTraceElement.getClassName
      if (className.isEmpty || !className.contains(PackageObjectSingletonClassName))
        return None

      for {
        packageObject <- ScalaShortNamesCacheManager.getInstance(project)
          .findPackageObjectByName(className.split('.').dropRight(1).mkString("."), scope)

        vFile <- packageObject.containingVirtualFile
      } yield (vFile, stackTraceElement.getLineNumber)
    }
  }

  private def updateLink(item: Filter.ResultItem, link: HyperlinkInfo): Filter.ResultItem = {
    new Filter.ResultItem(item.getHighlightStartOffset, item.getHighlightEndOffset, link, item.getHighlightAttributes)
  }
}
