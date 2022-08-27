package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.{DumbService, Project, ProjectUtil}
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
import org.junit.Assert.{assertNotNull, assertTrue}

import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.control.NonFatal
import scala.util.matching.Regex

trait AllProjectHighlightingTest {

  def getProject: Project

  def getProjectFixture: CodeInsightTestFixture

  protected val reporter: HighlightingProgressReporter

  def doAllProjectHighlightingTest(): Unit = {
    val modules = ModuleManager.getInstance(getProject).getModules
    val module = modules.find(_.hasScala)
    assertTrue("Test project doesn't have Scala SDK configured", module.isDefined)
    assertTrue("Test project must be in smart mode before running highlighting", !DumbService.isDumb(getProject))

    val scope = SourceFilterScope(scalaFileTypes :+ JavaFileType.INSTANCE)(getProject)
    val scalaFiles = scalaFileTypes.flatMap(fileType => FileTypeIndex.getFiles(fileType, scope).asScala)
    val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).asScala

    val allFilesSorted = (scalaFiles ++ javaFiles).sortBy(_.getPath)

    LocalFileSystem.getInstance().refreshFiles(allFilesSorted.asJava)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

    AllProjectHighlightingTest.warnIfUsingRandomizedTests(reporter)

    val filesTotal = allFilesSorted.size
    for ((file, fileIndex) <- allFilesSorted.zipWithIndex) {
      val psiFile = fileManager.findFile(file)

      val fileRelativePath = relativePathOf(psiFile)
      reporter.notifyHighlightingProgress(fileIndex, filesTotal, fileRelativePath)

      file.getFileType match {
        case JavaFileType.INSTANCE =>
          annotateJava(psiFile, getProjectFixture)
        case _ =>
          AllProjectHighlightingTest.annotateScalaFile(psiFile, reporter, Some(fileRelativePath))
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
}

object AllProjectHighlightingTest {
  //see com.intellij.openapi.vfs.ex.temp.TempFileSystem
  private val PathInTemporaryFileSystem = new Regex("temp:///.*?/(.*)")

  private val originalDirNameKey: Key[String] = Key.create("original.dir.name")

  private def getOriginalDirName(file: PsiFile): String =
    Option(file.getUserData(originalDirNameKey)).getOrElse("")

  def setOriginalDirName(file: PsiFile, dirName: String): Unit = {
    file.putUserData(originalDirNameKey, dirName)
  }

  //NOTE: looks like there is no clean API which could provide a project root path
  private def guessProjectRootUrl(project: Project): String = {
    val projectDir = ProjectUtil.guessProjectDir(project)
    assertNotNull(s"Cannot guess directory of project ${project.getName}", projectDir)
    projectDir.getUrl
  }

  private def relativePathOf(psiFile: PsiFile): String = {
    val fileUrl = psiFile.getVirtualFile.getUrl
    fileUrl match {
      case PathInTemporaryFileSystem(relative) =>
        val originalDir = getOriginalDirName(psiFile)
        s"$originalDir/$relative"
      case _ =>
        val projectRoot = guessProjectRootUrl(psiFile.getProject)
        if (fileUrl.startsWith(projectRoot)) {
          fileUrl.stripPrefix(projectRoot).stripPrefix("/")
        }
        else {
          throw new IllegalArgumentException(s"Unknown test path: $fileUrl (projectRoot: $projectRoot)")
        }
    }
  }


  /**
   * This seed can control the order in which the elements in scala files are annotated.
   * Should be None if we don't need elements order randomization
   *
   * Note, it should be enough to use the same seed for all tests in single JVM instance, anyway all files will contain different code.
   */
  private val randomSeedForRandomizedElementsProcessingOrder: Option[Long] = {
    //TODO: we don't want to run randomized tests in our main build chain, to have less flaky tests
    // We need to create a separate TeamCity configuration which would run nightly
    // and run project highlighting tests with randomization enabled
    val useRandomization = sys.props.get("project.highlighting.use.randomize.elements.order").contains("true")
    if (useRandomization)
      Some(System.currentTimeMillis())
    else
      None
  }

  def warnIfUsingRandomizedTests(reporter: HighlightingProgressReporter): Unit = {
    randomSeedForRandomizedElementsProcessingOrder match {
      case Some(seed) =>
        reporter.notify(
          s"""###
             |### Using randomized test with random seed: $seed
             |### File elements will be annotated in random order.
             |### See details in AllProjectHighlightingTest.randomSeedForRandomizedElementsProcessingOrder
             |###
             |""".stripMargin
        )
      case _ =>
    }
  }

  def annotateScalaFile(
    file: PsiFile,
    reporter: HighlightingProgressReporter,
    fileRelativePath: Option[String] = None
  ): Unit = {
    val scalaFile = file.getViewProvider.getPsi(ScalaLanguage.INSTANCE) match {
      case f: ScalaFile => f
      case _ => return
    }
    annotateScalaFile(scalaFile, reporter, fileRelativePath)
  }

  //noinspection InstanceOf
  private def annotateScalaFile(
    scalaFile: ScalaFile,
    reporter: HighlightingProgressReporter,
    fileRelativePathParam: Option[String],
  ): Unit = {
    val fileRelativePath = fileRelativePathParam.getOrElse(relativePathOf(scalaFile))
    val annotatorHolder: AnnotatorHolderMock = new AnnotatorHolderMock(scalaFile) {
      override def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[Message] = {
        if (severity == HighlightSeverity.ERROR) {
          reporter.reportError(fileRelativePath, range, message)
        }
        super.createMockAnnotation(severity, range, message)
      }
    }
    val annotator = new ScalaAnnotator()

    val elements = scalaFile.depthFirst().filter(_.isInstanceOf[ScalaPsiElement]).toSeq
    val randomSeed = randomSeedForRandomizedElementsProcessingOrder
    val elementsMaybeShuffled = randomSeed match {
      case Some(seed) =>
        val random = new Random(seed)
        random.shuffle(elements)
      case None =>
        elements
    }

    for ((element, elementIndex) <- elementsMaybeShuffled.zipWithIndex) { //zipWIthIndex for easier debugging
      try {
        annotator.annotate(element)(annotatorHolder)
      } catch {
        case ex: Throwable =>
          val message = s"Exception while highlighting element at index $elementIndex (${element.getText} - ${element.getNode.getTextRange}): $ex (random seed: $randomSeed)"
          reporter.reportError(fileRelativePath, element.getTextRange, message)
          if (!NonFatal(ex)) {
            throw ex
          }
      }
    }
  }
}
