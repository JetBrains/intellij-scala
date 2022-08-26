package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest.relativePathOf
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.control.NonFatal
import scala.util.matching.Regex

trait AllProjectHighlightingTest {

  def getProject: Project

  def getProjectFixture: CodeInsightTestFixture

  protected val reporter: HighlightingProgressReporter

  private val randomSeed: Long = System.currentTimeMillis()

  def doAllProjectHighlightingTest(): Unit = {
    val modules = ModuleManager.getInstance(getProject).getModules
    val module = modules.find(_.hasScala)
    assertTrue("Test project doesn't have Scala SDK configured", module.isDefined)
    assertTrue("Test project must be in smart mode before running highlighting", !DumbService.isDumb(getProject))

    val scope = SourceFilterScope(scalaFileTypes :+ JavaFileType.INSTANCE)(getProject)
    val scalaFiles = scalaFileTypes.flatMap(fileType => FileTypeIndex.getFiles(fileType, scope).asScala)
    val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).asScala

    val files = scalaFiles ++ javaFiles

    LocalFileSystem.getInstance().refreshFiles(files.asJava)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

    reporter.notify(
      s"""###
         |### Using randomized test with random seed: $randomSeed
         |### (file elements will be annotated in random order)
         |###
         |""".stripMargin
    )

    val filesTotal = files.size
    for ((file, fileIndex) <- files.zipWithIndex) {
      val psiFile = fileManager.findFile(file)

      reporter.notifyHighlightingProgress(fileIndex, filesTotal, file.getName)

      file.getFileType match {
        case JavaFileType.INSTANCE =>
          annotateJava(psiFile, getProjectFixture)
        case _ =>
          annotateScala(psiFile)
      }
    }

    reporter.reportFinalResults()
  }

  protected def scalaFileTypes: Seq[FileType] = Seq(ScalaFileType.INSTANCE)

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

  private def annotateScala(psiFile: PsiFile): Unit = {
    AllProjectHighlightingTest.annotateScalaFile(psiFile, reporter, Some(randomSeed))
  }
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

  def annotateScalaFile(
    file: PsiFile,
    reporter: HighlightingProgressReporter,
    randomSeed: Option[Long],
    fileRelativePath: Option[String] = None
  ): Unit = {
    val scalaFile = file.getViewProvider.getPsi(ScalaLanguage.INSTANCE) match {
      case f: ScalaFile => f
      case _ => return
    }
    annotateScalaFile(scalaFile, reporter, randomSeed, fileRelativePath)
  }

  /**
   * @param randomSeed pass some value to randomize elements processing order
   */
  private def annotateScalaFile(
    scalaFile: ScalaFile,
    reporter: HighlightingProgressReporter,
    randomSeed: Option[Long],
    fileRelativePath: Option[String],
  ): Unit = {
    val randomSeed = System.currentTimeMillis()
    val random = new Random(randomSeed)

    val fileName = fileRelativePath.getOrElse(relativePathOf(scalaFile))
    val annotatorHolder: AnnotatorHolderMock = new AnnotatorHolderMock(scalaFile) {
      override def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[Message] = {
        if (severity == HighlightSeverity.ERROR) {
          reporter.reportError(fileName, range, message)
        }
        super.createMockAnnotation(severity, range, message)
      }
    }

    val annotator = new ScalaAnnotator()

    val elements = scalaFile.depthFirst().filter(_.isInstanceOf[ScalaPsiElement]).toSeq
    val elementsShuffled = random.shuffle(elements)
    for ((element, elementIndex) <- elementsShuffled.zipWithIndex) { //zipWIthIndex for easier debugging
      try {
        annotator.annotate(element)(annotatorHolder)
      } catch {
        case ex: Throwable =>
          val message = s"Exception while highlighting element at index $elementIndex (${element.getText} - ${element.getNode.getTextRange}): $ex (random seed: $randomSeed)"
          reporter.reportError(fileName, element.getTextRange, message)
          if (!NonFatal(ex)) {
            throw ex
          }
      }
    }
  }

  val originalDirNameKey: Key[String] = Key.create("original.dir.name")
}
