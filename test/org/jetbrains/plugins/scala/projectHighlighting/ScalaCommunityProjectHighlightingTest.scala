package org.jetbrains.plugins.scala.projectHighlighting

import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.{HighlightingTests, ScalaFileType}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.performance.{DownloadingAndImportingTestCase, ScalaCommunityDownloadingAndImportingTestCase}
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaCommunityProjectHighlightingTest extends DownloadingAndImportingTestCase with ScalaCommunityDownloadingAndImportingTestCase {

  override def revision: String = "a9ac902e8930c520b390095d9e9346d9ae546212"

  class DefaultReporter {
    var totalErrors = 0
    def reportError(file: VirtualFile, range: TextRange, message: String) = {
      totalErrors += 1
      println(s"Error: ${file.getName}${range.toString} - $message")
    }
    def updateProgess(percent: Int) = println(s"Highlighting -  $percent%")
    def reportResults() = {
        Assert.assertTrue(s"Found $totalErrors errors while highlighting the project", totalErrors == 0)
    }
  }

  class TCReporter extends DefaultReporter {
    override def updateProgess(percent: Int): Unit = println(s"##teamcity[progressMessage 'Highlighting - $percent%']")

    override def reportError(file: VirtualFile, range: TextRange, message: String): Unit = {
      super.reportError(file, range, message)
      val testName = s"${getClass.getName}.${file.getName}$range"
      println(
        s"""
          |##teamcity[testStarted name='$testName']
          |##teamcity[testFailed name='$testName' message='Highlighting error' details='$message']
          |##teamcity[testFinished name='$testName']
        """.stripMargin)
    }

    override def reportResults(): Unit = {
      println("##teamcity[buildProblem description='Found $totalErrors errors while highlighting the project' ]")
    }
  }

  def testAllHighlighting(): Unit = {
    import scala.collection.JavaConversions._

    val reporter = if (sys.env.contains("TEAMCITY_VERSION"))
      new TCReporter
    else
      new DefaultReporter

    val searchScope =
      new SourceFilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(myProject),
        ScalaFileType.SCALA_FILE_TYPE, JavaFileType.INSTANCE), myProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.SCALA_FILE_TYPE, searchScope)

    LocalFileSystem.getInstance().refreshFiles(files)

    val fileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager
    val annotator = new ScalaAnnotator

    var percent = 0
    val size: Int = files.size()

    for ((file, index) <- files.zipWithIndex) {
      val mock = new AnnotatorHolderMock {
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
        reporter.updateProgess(percent)
      }

      val psi = fileManager.findFile(file)

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
      psi.accept(visitor)
    }

    reporter.reportResults()
  }
}
