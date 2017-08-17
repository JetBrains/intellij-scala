package org.jetbrains.plugins.scala.projectHighlighting

import java.util

import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

import scala.util.control.NonFatal
import scala.collection.JavaConverters._

/**
  * @author Mikhail Mutcianko
  * @since 30.08.16
  */
trait AllProjectHighlightingTest {

  def getProject: Project

  implicit def projectContext: ProjectContext = getProject

  def doAllProjectHighlightingTest(): Unit = {

    val reporter = ProgressReporter.newInstance

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, SourceFilterScope(getProject))

    LocalFileSystem.getInstance().refreshFiles(files)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager
    val annotator = ScalaAnnotator.forProject

    var percent = 0
    val size: Int = files.size()

    for ((file, index) <- files.asScala.zipWithIndex) {
      val psiFile = fileManager.findFile(file)

      if ((index + 1) * 100 >= (percent + 1) * size) {
        while ((index + 1) * 100 >= (percent + 1) * size) percent += 1
        reporter.updateHighlightingProgress(percent)
      }

      AllProjectHighlightingTest.annotateFile(psiFile, reporter)
    }

    reporter.reportResults()
  }
}

object AllProjectHighlightingTest {

  def annotateFile(psiFile: PsiFile, reporter: ProgressReporter): Unit = {
    val fileName = psiFile.getName
    val mock = new AnnotatorHolderMock(psiFile){
      override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
        reporter.reportError(fileName, range, message)
        super.createErrorAnnotation(range, message)
      }

      override def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
        createErrorAnnotation(elt.getTextRange, message)
      }
    }

    val annotator = ScalaAnnotator.forProject(psiFile)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitElement(element: ScalaPsiElement) {
        try {
          annotator.annotate(element, mock)
        } catch {
          case NonFatal(t) => reporter.reportError(fileName, element.getTextRange, s"Exception while highlighting: $t")
        }
        super.visitElement(element)
      }
    }

    psiFile.accept(visitor)
  }
}
