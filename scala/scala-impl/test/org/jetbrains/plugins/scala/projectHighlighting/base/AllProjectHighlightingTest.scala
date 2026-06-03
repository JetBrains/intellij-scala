package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest.relativePathOf
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.TestUtils
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

  protected def scalaFileTypes: Seq[FileType] = Seq(ScalaFileType.INSTANCE)

  protected def processOnlyFilesInSourceRoots: Boolean = true

  def doAllProjectHighlightingTest(): Unit = {
    val modules = ModuleManager.getInstance(getProject).getModules
    val module = modules.find(_.hasScala)
    assertTrue(
      """Test project doesn't have Scala SDK configured.
        |Probably something went wrong during project import.
        |Please see build logs for the details.""".stripMargin,
      module.isDefined
    )
    assertTrue("Test project must be in smart mode before running highlighting", !DumbService.isDumb(getProject))

    val fileTypes = scalaFileTypes :+ JavaFileType.INSTANCE
    val scope = if (processOnlyFilesInSourceRoots)
      SourceFilterScope(fileTypes)(getProject)
    else
      GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(getProject), fileTypes: _*)
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

      highlightSingleFile(file, psiFile, reporter)
    }

    reporter.reportFinalResults()
  }

  //NOTE: it can be overridden
  //By default it simply runs Java/Scala annotator
  protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit = {
    virtualFile.getFileType match {
      case JavaFileType.INSTANCE =>
        AllProjectHighlightingTest.annotateJava(psiFile, getProjectFixture, reporter)
      case _ =>
        AllProjectHighlightingTest.annotateScalaFile(psiFile, reporter)
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
    val projectDir = TestUtils.guessProjectDir(project)
    projectDir.getUrl
  }

  def relativePathOf(psiFile: PsiFile): String = {
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
  ): Unit = {
    annotateScalaFile(file, reporter, relativePathOf(file))
  }

  def annotateScalaFile(
    file: PsiFile,
    reporter: HighlightingProgressReporter,
    fileRelativePath: String
  ): Unit = {
    val scalaFile = file.getViewProvider.getPsi(ScalaLanguage.INSTANCE) match {
      case f: ScalaFile => f
      case _ => return
    }
    annotateScalaFile(scalaFile, reporter, Some(fileRelativePath))
  }

  //noinspection InstanceOf
  private def annotateScalaFile(
    scalaFile: ScalaFile,
    reporter: HighlightingProgressReporter,
    fileRelativePathParam: Option[String] = None
  ): Unit = {
    val fileRelativePath = fileRelativePathParam.getOrElse(relativePathOf(scalaFile))
    val annotatorHolder: AnnotatorHolderMock = new AnnotatorHolderMock(scalaFile) {
      override def createMockAnnotation(
        severity: HighlightSeverity,
        range: TextRange,
        message: String,
        enforcedAttributes: TextAttributesKey,
        fixes: Seq[CommonIntentionAction]
      ): Option[Message] = {
        if (severity == HighlightSeverity.ERROR) {
          reporter.reportError(fileRelativePath, range, message)
        }
        super.createMockAnnotation(severity, range, message, enforcedAttributes, fixes)
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

  private def annotateJava(
    psiFile: PsiFile,
    codeInsightFixture: CodeInsightTestFixture,
    reporter: HighlightingProgressReporter,
  ): Unit = {
    codeInsightFixture.openFileInEditor(psiFile.getVirtualFile)
    val allInfo = codeInsightFixture.doHighlighting().asScala.toSeq

    val errors = allInfo.filter(_.`type`.getSeverity(null) == HighlightSeverity.ERROR)
    errors.foreach { error =>
      val range = TextRange.create(error.getStartOffset, error.getEndOffset)
      reporter.reportError(relativePathOf(psiFile), range, error.getDescription)
    }
  }
}
