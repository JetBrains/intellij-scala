package org.jetbrains.plugins.scala.projectHighlighting

import java.util

import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

import scala.util.control.NonFatal

/**
  * @author Mikhail Mutcianko
  * @since 30.08.16
  */
trait AllProjectHighlightingTest {

  def getProject: Project

  implicit def projectContext: ProjectContext = getProject

  def doAllProjectHighlightingTest(): Unit = {
    import scala.collection.JavaConversions._

    val reporter = ProgressReporter.getInstance

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, SourceFilterScope(getProject))

    LocalFileSystem.getInstance().refreshFiles(files)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager
    val annotator = ScalaAnnotator.forProject

    var percent = 0
    val size: Int = files.size()

    for ((file, index) <- files.zipWithIndex) {
      val psiFile = fileManager.findFile(file)

      val mock = new AnnotatorHolderMock(psiFile){
        override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
          // almost always duplicates reports from method below
//          reporter.reportError(file, range, message)
          super.createErrorAnnotation(range, message)
        }

        override def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
          reporter.reportError(file, elt.getTextRange, message)
          super.createErrorAnnotation(elt, message)
        }
      }

      if ((index + 1) * 100 >= (percent + 1) * size) {
        while ((index + 1) * 100 >= (percent + 1) * size) percent += 1
        reporter.updateHighlightingProgress(percent)
      }

      val visitor = new ScalaRecursiveElementVisitor {
        override def visitElement(element: ScalaPsiElement) {
          try {
            annotator.annotate(element, mock)
          } catch {
            case NonFatal(t) => reporter.reportError(file, element.getTextRange, s"Exception while highlighting: $t")
          }
          super.visitElement(element)
        }
      }
      psiFile.accept(visitor)
    }

    reporter.reportResults()
  }
}
