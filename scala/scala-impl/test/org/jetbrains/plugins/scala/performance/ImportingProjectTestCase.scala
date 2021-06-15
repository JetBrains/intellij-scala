package org.jetbrains.plugins.scala
package performance

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScopesCore}
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, IdeaTestFixtureFactory}
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.performance.ImportingProjectTestCase.isCachingEnabled
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert
/**
  * Nikolay.Tropin
  * 14-Dec-17
  */
abstract class ImportingProjectTestCase extends ExternalSystemImportingTestCase {

  protected var codeInsightFixture: CodeInsightTestFixture = _

  private val projectFileName = "testHighlighting"
  private def getProjectFilePath: Path = Paths.get(projectDirPath, s"$projectFileName.ipr")
  private def isProjectAlreadyCached = Files.exists(getProjectFilePath)

  override def setUpFixtures(): Unit = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val projectFixture =
      if (isProjectAlreadyCached && isCachingEnabled)
        new FixtureDelegate(getProjectFilePath)
      else
        factory.createFixtureBuilder(getName).getFixture
    codeInsightFixture = factory.createCodeInsightFixture(projectFixture)
    codeInsightFixture.setUp()
    myTestFixture = codeInsightFixture
  }

  private def persistProjectConfiguration(): Unit = {
    Option(myProject.getProjectFile).foreach { projectFile =>
      Files.copy(projectFile.toNioPath, getProjectFilePath)
    }
    Option(myProject.getWorkspaceFile).foreach { workspaceFile =>
      if (workspaceFile.exists())
        Files.copy(myProject.getWorkspaceFile.toNioPath, Paths.get(projectDirPath, s"$projectFileName.iws"))
    }
  }

  override protected def tearDownFixtures(): Unit = {
    if (!isProjectAlreadyCached && isCachingEnabled)
      persistProjectConfiguration()
    codeInsightFixture.tearDown()
    codeInsightFixture = null
    myTestFixture = null
  }

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val sbtSettings = SbtSettings.getInstance(myProject)
    sbtSettings.setVmParameters(sbtSettings.vmParameters + s"-Dsbt.ivy.home=$rootDirPath/.ivy_cache")

    val settings = new SbtProjectSettings
    settings.jdk = getJdk.getName
    settings
  }

  def filesWithProblems: Map[String, Set[TextRange]] = Map.empty

  protected val reporter = ProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getTestsTempDir: String = ""

  def rootDirPath: String

  def projectName: String

  def projectDirPath: String = s"$rootDirPath/$projectName"

  def doBeforeImport(): Unit = {}

  def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_11

  override def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()

    doBeforeImport()

    val projectDir = new File(projectDirPath)

    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    extensions.inWriteAction {
      val jdk = getJdk

      val jdkTable = ProjectJdkTable.getInstance
      if (jdkTable.findJdk(jdk.getName) == null) {
        jdkTable.addJdk(jdk, myProject.unloadAwareDisposable)
      }
      ProjectRootManager.getInstance(myProject).setProjectSdk(jdk)
      reporter.notify("Finished sbt setup, starting import")
    }
  }

  private def forceSaveProject(): Unit = {
    val app = ApplicationManagerEx.getApplicationEx
    try {
      app.setSaveAllowed(true)
      myProject.save()
    } finally {
      app.setSaveAllowed(false)
    }
  }

  override def setUp(): Unit = {
    super.setUp()
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
    if (!isProjectAlreadyCached || !isCachingEnabled)
      importProject()
    else
      reporter.notify("Project caching enabled, reusing last sbt import")
    if (isCachingEnabled)
      forceSaveProject()
  }

  override def tearDown(): Unit = {
    inWriteAction {
      ProjectJdkTable.getInstance().removeJdk(getJdk)
    }
    super.tearDown()
  }

  protected def findFile(filename: String): VirtualFile = {
    import scala.jdk.CollectionConverters._
    val searchScope = SourceFilterScope(GlobalSearchScopesCore.directoryScope(myProject, myProjectRoot, true))(myProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, searchScope)
    val file = files.asScala.filter(_.getName == filename).toList match {
      case vf :: Nil => vf
      case Nil => // is this a file path?
        val file = VfsTestUtil.findFileByCaseSensitivePath(s"$projectDirPath/$filename")
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

  def getJdk: Sdk = SmartJDKLoader.getOrCreateJDK(jdkLanguageLevel)

}
object ImportingProjectTestCase {
  private final val isCachingEnabled = !sys.props.get("project.highlighting.disable.cache").contains("true")
}
