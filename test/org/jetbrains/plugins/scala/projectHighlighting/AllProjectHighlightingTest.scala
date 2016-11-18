package org.jetbrains.plugins.scala.projectHighlighting

import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.junit.Assert

/**
  * @author Mikhail Mutcianko
  * @since 30.08.16
  */
trait AllProjectHighlightingTest {

  def getProject: Project

  class DefaultReporter {
    var totalErrors = 0
    def reportError(file: VirtualFile, range: TextRange, message: String) = {
      totalErrors += 1
      println(s"Error: ${file.getName}${range.toString} - $message")
    }
    def updateProgress(percent: Int) = println(s"Highlighting -  $percent%")
    def reportResults() = {
      Assert.assertTrue(s"Found $totalErrors errors while highlighting the project", totalErrors == 0)
    }
  }

  class TCReporter extends DefaultReporter {

    def escapeTC(message: String): String = {
      message
        .replaceAll("##", "")
        .replaceAll("([\"\'\n\r\\|\\[\\]])", "\\|$1")
    }

    override def updateProgress(percent: Int): Unit = println(s"##teamcity[progressMessage 'Highlighting - $percent%']")

    override def reportError(file: VirtualFile, range: TextRange, message: String): Unit = {
      totalErrors += 1
      val escaped = escapeTC(Option(message).getOrElse(""))
      val testName = s"${getClass.getName}.${Option(file).map(_.getName).getOrElse("UNKNOWN")}${Option(range).map(_.toString).getOrElse("(UNKNOWN)")}"
      println(s"##teamcity[testStarted name='$testName']")
      println(s"##teamcity[testFailed name='$testName' message='Highlighting error' details='$escaped']")
      println(s"##teamcity[testFinished name='$testName']")
    }

    override def reportResults(): Unit = {
      if (totalErrors > 0)
        println(s"##teamcity[buildProblem description='Found $totalErrors errors while highlighting the project' ]")
      else
        println("##teamcity[buildStatus status='SUCCESS' text='No highlighting errors found in project']")
    }
  }

  def doAllProjectHighlightingTest(): Unit = {
    import scala.collection.JavaConversions._

    val reporter = if (sys.env.contains("TEAMCITY_VERSION"))
      new TCReporter
    else
      new DefaultReporter

    val searchScope =
      new SourceFilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(getProject),
        ScalaFileType.SCALA_FILE_TYPE, JavaFileType.INSTANCE), getProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.SCALA_FILE_TYPE, searchScope)

    LocalFileSystem.getInstance().refreshFiles(files)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager
    val annotator = new ScalaAnnotator

    var percent = 0
    val size: Int = files.size()

    for ((file, index) <- files.zipWithIndex) {
      val psiFile = fileManager.findFile(file)

      val mock = new AnnotatorHolderMock(psiFile){
        override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
          reporter.reportError(file, range, message)
          super.createErrorAnnotation(range, message)
        }

        override def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
          reporter.reportError(file, elt.getTextRange, message)
          super.createErrorAnnotation(elt, message)
        }
      }

      if ((index + 1) * 100 >= (percent + 1) * size) {
        while ((index + 1) * 100 >= (percent + 1) * size) percent += 1
        reporter.updateProgress(percent)
      }

      val visitor = new ScalaRecursiveElementVisitor {
        override def visitElement(element: ScalaPsiElement) {
          try {
            annotator.annotate(element, mock)
          } catch {
            case e: Throwable =>
              println(s"Exception in ${file.getName}, Stacktrace: ")
              e.printStackTrace()
              assert(false)
          }
          super.visitElement(element)
        }
      }
      psiFile.accept(visitor)
    }

    reporter.reportResults()
  }
}
