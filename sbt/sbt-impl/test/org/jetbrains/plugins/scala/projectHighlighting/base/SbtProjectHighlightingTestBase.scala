package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiFile
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScopesCore}
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, IdeaTestFixtureFactory}
import com.intellij.testFramework.{TestLoggerFactory, VfsTestUtil}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.performance.FixtureDelegate
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest.relativePathOf
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestCase
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class SbtProjectHighlightingTestBase extends SbtExternalSystemImportingTestCase {

  protected var codeInsightFixture: CodeInsightTestFixture = _

  private val projectFileName = "testHighlighting"
  private def getProjectFilePath: Path = Paths.get(getTestProjectPath, s"$projectFileName.ipr")
  private def isProjectAlreadyCached = Files.exists(getProjectFilePath)

  protected def isProjectCachingEnabled: Boolean =
    !SbtProjectHighlightingTestBase.isProjectCachingDisabledPropertySet

  override def setUpFixtures(): Unit = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory

    val projectFixture =
      if (isProjectAlreadyCached && isProjectCachingEnabled)
        new FixtureDelegate(getProjectFilePath)
      else
        factory.createFixtureBuilder(getName).getFixture

    codeInsightFixture = factory.createCodeInsightFixture(projectFixture)
    codeInsightFixture.setUp()
    myTestFixture = codeInsightFixture
  }

  protected final def doHighlightingForFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit = {
    codeInsightFixture.openFileInEditor(virtualFile)
    val infosAll = codeInsightFixture.doHighlighting().asScala.toSeq
    val infosWithDescription = infosAll.filter(_.getDescription != null)
    infosWithDescription.foreach { error =>
      val range = TextRange.create(error.getStartOffset, error.getEndOffset)
      reporter.reportError(relativePathOf(psiFile), range, error.getDescription)
    }
  }

  private def persistProjectConfiguration(): Unit = {
    reporter.notify(s"Saving project configuration")

    val projectFile = myProject.getProjectFile
    if (projectFile != null) {
      val from = projectFile.toNioPath
      val to = getProjectFilePath
      reporter.notify(s"Copy project file $from to $to")
      Files.copy(from, to)
    }

    val workspaceFile = myProject.getWorkspaceFile
    if (workspaceFile != null && workspaceFile.exists()) {
      val from = workspaceFile.toNioPath
      val to = Paths.get(getTestProjectPath, s"$projectFileName.iws")
      reporter.notify(s"Copy workspace file $from to $to")
      Files.copy(from, to)
    }
  }

  override protected def tearDownFixtures(): Unit = {
    if (isProjectCachingEnabled && !isProjectAlreadyCached) {
      persistProjectConfiguration()
    }

    codeInsightFixture.tearDown()
    codeInsightFixture = null
    myTestFixture = null
  }

  protected def filesWithProblems: Map[String, Set[TextRange]] = Map.empty

  protected val reporter = HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)

  //examples:
  // `.../testdata/projectsForHighlightingTests/local`
  // `.../testdata/projectsForHighlightingTests/downloaded`
  protected def rootProjectsDirPath: String

  protected def projectName: String

  override protected def getTestProjectPath: String = s"$rootProjectsDirPath/$projectName"

  private def forceSaveProject(): Unit = {
    val app = ApplicationManagerEx.getApplicationEx
    try {
      app.setSaveAllowed(true)
      myProject.save()
    } finally {
      app.setSaveAllowed(false)
    }
  }

  override def setUp(): Unit = dumpLogsOnException {
    super.setUp()
    reporter.notify("Finished sbt setup, starting import")

    //patch homes before importing projects
    patchIvyAndCoursierHomeDirsForSbt()

    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)

    if (isProjectAlreadyCached && isProjectCachingEnabled) {
      reporter.notify(
        """!!!
          |!!! Project caching enabled:
          |!!! Skipping sbt project import and reusing results of previous import
          |!!! (you can disable caching by passing -Dproject.highlighting.disable.cache=true VM option)
          |!!! """.stripMargin
      )
    } else {
      importProject(false)
    }
    if (isProjectCachingEnabled) {
      forceSaveProject()
    }
  }

  private final def ivyAndCoursierCachesRootPath: String =
    SbtProjectHighlightingTestBase.projectsForHighlightingTestsRootPath

  private def patchIvyAndCoursierHomeDirsForSbt(): Unit = {
    //TODO: ensure ivy caches are reused on the server
    val sbtSettings = SbtSettings.getInstance(myProject)
    val ivyHome = s"$ivyAndCoursierCachesRootPath/.ivy_cache"
    val coursierHome = s"$ivyAndCoursierCachesRootPath/.coursier_cache"

    reporter.notify(
      s"""Patching Ivy and Coursier home directories:
         |ivy home      : $ivyHome
         |coursier home : $coursierHome
         |""".stripMargin)

    val vmOptionsUpdated = sbtSettings.vmParameters +
      s" -Dsbt.ivy.home=$ivyHome" +
      s" -Dsbt.coursier.home=$coursierHome"

    sbtSettings.setVmParameters(vmOptionsUpdated)
  }

  /**
   * This methods basically duplicates log dumping logic from [[com.intellij.testFramework.UsefulTestCase#wrapTestRunnable]].
   * By default logs are not dumped in there was a failure in `setUp` method. But wee need them to debug failures
   * during project importing process, which is done in `setUp` method.
   *
   * Actually, I wish this logic was implemented in the platform (both for `setUp` & `tearDown` methods).
   * If there is a willing we might discuss it with IntelliJ platform team.
   */
  private def dumpLogsOnException(body: => Unit): Unit = {
    val testDescription = "dummy test description"
    TestLoggerFactory.onTestStarted()

    var success = true
    try {
      body
    } catch {
      case t: Throwable =>
        success = false
        throw t
    }
    finally {
      TestLoggerFactory.onTestFinished(success, testDescription)
    }
  }

  protected def findFile(filename: String): VirtualFile = {
    import scala.jdk.CollectionConverters._
    val searchScope = SourceFilterScope(GlobalSearchScopesCore.directoryScope(myProject, myProjectRoot, true))(myProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, searchScope)
    val file = files.asScala.filter(_.getName == filename).toList match {
      case vf :: Nil => vf
      case Nil => // is this a file path?
        val file = VfsTestUtil.findFileByCaseSensitivePath(s"$getTestProjectPath/$filename")
        Assert.assertTrue(
          s"Could not find file: $filename. Consider providing relative path from project root",
          file != null && files.contains(file)
        )
        file
      case list =>
        Assert.fail(s"There are ${list.size} files with name $filename. Provide full path from project root")
        null
    }
    LocalFileSystem.getInstance().refreshFiles(files)
    file
  }
}

object SbtProjectHighlightingTestBase {
  private val isProjectCachingDisabledPropertySet: Boolean =
    sys.props.get("project.highlighting.disable.cache").contains("true")

  //NOTE: when updating, please also update `org.jetbrains.scalateamcity.common.Caching.highlightingPatterns`
  def projectsForHighlightingTestsRootPath: String = s"${TestUtils.getTestDataPath}/projectsForHighlightingTests"
}
