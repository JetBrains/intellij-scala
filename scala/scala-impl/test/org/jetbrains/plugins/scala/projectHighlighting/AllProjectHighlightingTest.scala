package org.jetbrains.plugins.scala
package projectHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotation, ScalaAnnotator}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.projectHighlighting.AllProjectHighlightingTest.relativePathOf
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.control.NonFatal
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

    val scope = SourceFilterScope(scalaFileTypes :+ JavaFileType.INSTANCE)(getProject)
    val scalaFiles = scalaFileTypes.flatMap(fileType => FileTypeIndex.getFiles(fileType, scope).asScala)
    val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).asScala

    val files = scalaFiles ++ javaFiles

    LocalFileSystem.getInstance().refreshFiles(files.asJava)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

    val size: Int = files.size

    for ((file, index) <- files.zipWithIndex if !shouldSkip(file.getName)) {
      val psiFile = fileManager.findFile(file)

      reporter.updateHighlightingProgress(percent(index, size), file.getName)

      file.getFileType match {
        case JavaFileType.INSTANCE =>
          annotateJava(psiFile, getProjectFixture)
        case _ =>
          annotateScala(psiFile)
      }
    }

    reporter.reportResults()
  }

  protected def scalaFileTypes: Seq[FileType] = Seq(ScalaFileType.INSTANCE)

  def shouldSkip(fileName: String): Boolean = false

  private def percent(index: Int, size: Int): Int = (index + 1) * 100 / size

  private def annotateJava(psiFile: PsiFile, codeInsightFixture: CodeInsightTestFixture): Unit = {
    codeInsightFixture.openFileInEditor(psiFile.getVirtualFile)
    val allInfo = codeInsightFixture.doHighlighting()

    import scala.jdk.CollectionConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        val range = TextRange.create(highlightInfo.getStartOffset, highlightInfo.getEndOffset)
        reporter.reportError(relativePathOf(psiFile), range, highlightInfo.getDescription)
    }
  }

  private def annotateScala(psiFile: PsiFile): Unit =
    AllProjectHighlightingTest.annotateScalaFile(psiFile, reporter)
}

object AllProjectHighlightingTest {
  private val RemotePath = new Regex(".*/projects/.*?/(.*)")
  private val LocalPath = new Regex(".*/localProjects/.*?/(.*)")
  private val ScalacPath = new Regex("temp:///.*?/(.*)")

  private def originalDirName(file: PsiFile): String = Option(file.getUserData(originalDirNameKey)).getOrElse("")

  private def relativePathOf(psiFile: PsiFile): String = psiFile.getVirtualFile.getUrl match {
    case ScalacPath(relative) => originalDirName(psiFile) + "/" + relative
    case LocalPath(relative) => relative
    case RemotePath(relative) => relative
    case path => throw new IllegalArgumentException(s"Unknown test path: $path")
  }

  def annotateScalaFile(file: PsiFile, reporter: ProgressReporter, relPath: Option[String] = None): Unit = {
    val scalaFile = file.getViewProvider.getPsi(ScalaLanguage.INSTANCE) match {
      case f: ScalaFile => f
      case _            => return
    }

    val fileName = relPath.getOrElse(relativePathOf(scalaFile))
    val mock = new AnnotatorHolderMock(scalaFile){
      override def createErrorAnnotation(range: TextRange, message: String): ScalaAnnotation = {
        reporter.reportError(fileName, range, message)
        super.createErrorAnnotation(range, message)
      }

      override def createErrorAnnotation(elt: PsiElement, message: String): ScalaAnnotation = {
        createErrorAnnotation(elt.getTextRange, message)
      }
    }

    val annotator = ScalaAnnotator.forProject(scalaFile)

    val random = new Random(0)

    for (element <- random.shuffle(scalaFile.depthFirst().filter(_.isInstanceOf[ScalaPsiElement]))) {
      try {
        annotator.annotate(element)(mock)
      } catch {
        case NonFatal(t) =>
          reporter.reportError(fileName, element.getTextRange, s"Exception while highlighting: $t")
      }
    }
  }

  val originalDirNameKey: Key[String] = Key.create("original.dir.name")
}
