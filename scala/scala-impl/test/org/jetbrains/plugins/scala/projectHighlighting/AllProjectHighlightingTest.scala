package org.jetbrains.plugins.scala.projectHighlighting

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.{Annotation, HighlightSeverity}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.projectHighlighting.AllProjectHighlightingTest.relativePathOf
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

import scala.util.matching.Regex

/**
  * @author Mikhail Mutcianko
  * @since 30.08.16
  */
trait AllProjectHighlightingTest {

  def getProject: Project

  def getProjectFixture: CodeInsightTestFixture

  protected def reporter: ProgressReporter

  def doAllProjectHighlightingTest(): Unit = {

    val scope = SourceFilterScope(getProject)
    val scalaFiles = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, scope)
    val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)

    val files = scalaFiles.asScala ++ javaFiles.asScala

    LocalFileSystem.getInstance().refreshFiles(files.asJava)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

    val size: Int = files.size

    for ((file, index) <- files.zipWithIndex if !shouldSkip(file.getName)) {
      val psiFile = fileManager.findFile(file)

      reporter.updateHighlightingProgress(percent(index, size))

      file.getFileType match {
        case ScalaFileType.INSTANCE =>
          annotateScala(psiFile)
        case JavaFileType.INSTANCE =>
          annotateJava(psiFile, getProjectFixture)
      }
    }

    reporter.reportResults()
  }

  def shouldSkip(fileName: String): Boolean = false

  private def percent(index: Int, size: Int): Int = (index + 1) * 100 / size

  private def annotateJava(psiFile: PsiFile, codeInsightFixture: CodeInsightTestFixture): Unit = {
    codeInsightFixture.openFileInEditor(psiFile.getVirtualFile)
    val allInfo = codeInsightFixture.doHighlighting()

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        val range = TextRange.create(highlightInfo.getStartOffset, highlightInfo.getEndOffset)
        reporter.reportError(relativePathOf(psiFile), range, highlightInfo.getDescription)
    }
  }

  private def annotateScala(psiFile: PsiFile): Unit =
    AllProjectHighlightingTest.annotateFile(psiFile, reporter)
}

object AllProjectHighlightingTest {
  private val RemotePath = new Regex(".*/projects/.*?/(.*)")
  private val LocalPath = new Regex(".*/localProjects/.*?/(.*)")
  private val ScalacPath = new Regex("temp:///.*?/(.*)")

  private def relativePathOf(psiFile: PsiFile): String = psiFile.getVirtualFile.getUrl match {
    case ScalacPath(relative) => relative
    case LocalPath(relative) => relative
    case RemotePath(relative) => relative
    case path => throw new IllegalArgumentException(s"Unknown test path: $path")
  }

  def annotateFile(psiFile: PsiFile, reporter: ProgressReporter, relPath: Option[String] = None): Unit = {
    val fileName = relPath.getOrElse(relativePathOf(psiFile))
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
