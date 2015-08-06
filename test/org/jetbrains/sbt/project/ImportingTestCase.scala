package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore}
import com.intellij.util.{CommonProcessors, PathUtil}
import junit.framework.Assert.{assertTrue, fail}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 8/4/15.
 */
abstract class ImportingTestCase extends ExternalSystemImportingTestCase {

  import ImportingTestCase._

  def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit

  def getTestProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def scalaPluginBuildOutputDir: File = new File("../../out/plugin/Scala")

  def getProject: Project = myProject

  def assertProjectsEqual(expected: project): Unit = {
    assertEquals("Project name", expected.name, getProject.getName)
    assertProjectSdkEquals(expected)
    assertProjectLanguageLevelEquals(expected)
    assertProjectModulesEqual(expected)
    assertProjectLibrariesEqual(expected)
  }

  def runTest(expected: project): Unit = {
    importProject()
    assertProjectsEqual(expected)
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    settings.setJdk(internalSdk.getName)
    settings
  }

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    setUpProjectDirectory()
    setUpSbtLauncherAndStructure()
    setUpExternalSystemToPerformImportInIdeaProcess()
  }

  private implicit val ideaModuleNameImplicit = new HasName[Module] {
    override def apply(module: Module): String = module.getName
  }

  private implicit val ideaLibraryNameImplicit = new HasName[Library] {
    override def apply(library: Library): String = library.getName
  }

  private implicit val ideaModuleEntryNameImplicit = new HasName[roots.ModuleOrderEntry] {
    override def apply(entry: roots.ModuleOrderEntry): String = entry.getModuleName
  }

  private implicit val ideaLibraryEntryNameImplicit = new HasName[roots.LibraryOrderEntry] {
    override def apply(entry: roots.LibraryOrderEntry): String = entry.getLibraryName
  }

  private def setUpProjectDirectory(): Unit =
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(getTestProjectDir)

  private def setUpSbtLauncherAndStructure(): Unit = {
    val systemSettings = SbtSystemSettings.getInstance(myProject)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher/sbt-launch.jar")
    systemSettings.setCustomSbtStructureDir(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher")
  }

  private def setUpExternalSystemToPerformImportInIdeaProcess(): Unit =
    Registry.get(SbtProjectSystem.Id + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX).setValue(true)

  private def assertProjectSdkEquals(expected: project): Unit =
    expected.foreach(sdk)(it => assertEquals("Project SDK", it, roots.ProjectRootManager.getInstance(getProject).getProjectSdk))

  private def assertProjectLanguageLevelEquals(expected: project): Unit =
    expected.foreach(languageLevel)(it => assertEquals("Project language level", it, roots.LanguageLevelProjectExtension.getInstance(getProject).getLanguageLevel))

  private def assertProjectModulesEqual(expected: project): Unit =
    expected.foreach(modules) { expectedModules =>
      val actualModules = ModuleManager.getInstance(getProject).getModules.toSeq
      assertNamesEqual("Project module", expectedModules, actualModules)
      pairByName(expectedModules, actualModules).foreach((assertModulesEqual _).tupled)
    }

  private def assertModulesEqual(expected: module, actual: Module): Unit = {
    expected.foreach(contentRoots)(assertModuleContentRootsEqual(actual))
    expected.foreach(ProjectStructureDsl.sources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE))
    expected.foreach(testSources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE))
    expected.foreach(resources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE))
    expected.foreach(testResources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE))
    expected.foreach(excluded)(assertModuleExcludedFoldersEqual(actual))
    expected.foreach(moduleDependencies)(assertModuleDependenciesEqual(actual))
    expected.foreach(libraryDependencies)(assertLibraryDependenciesEqual(actual))
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String]): Unit = {
    val expectedRoots = expected.map(VfsUtilCore.pathToUrl)
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl)
    assertMatch("Content root", expectedRoots, actualRoots)
  }

  private def assertModuleContentFoldersEqual(module: Module, folderType: JpsModuleSourceRootType[_])(expected: Seq[String]): Unit = {
    val contentRoot = getSingleContentRoot(module)
    assertContentRootFoldersEqual(contentRoot, contentRoot.getSourceFolders(folderType).asScala, expected)
  }

  private def assertModuleExcludedFoldersEqual(module: Module)(expected: Seq[String]): Unit = {
    val contentRoot = getSingleContentRoot(module)
    assertContentRootFoldersEqual(contentRoot, contentRoot.getExcludeFolders, expected)
  }

  private def assertContentRootFoldersEqual(contentRoot: roots.ContentEntry, actual: Seq[roots.ContentFolder], expected: Seq[String]): Unit = {
    val actualFolders = actual.map { folder =>
      val folderUrl = folder.getUrl
      if (folderUrl.startsWith(contentRoot.getUrl))
        folderUrl.substring(Math.min(folderUrl.length, contentRoot.getUrl.length + 1))
      else
        folderUrl
    }
    assertMatch("Content folder", expected, actualFolders)
  }

  private def getSingleContentRoot(module: Module): roots.ContentEntry = {
    val contentRoots = roots.ModuleRootManager.getInstance(module).getContentEntries
    assertEquals(s"Expected single content root, Got: $contentRoots", 1, contentRoots.length)
    contentRoots.head
  }

  private def assertModuleDependenciesEqual(module: Module)(expected: Seq[dependency[module]]): Unit = {
    val actualModuleEntries = roots.OrderEnumerator.orderEntries(module).moduleEntries
    assertNamesEqual("Module dependency", expected.map(_.reference), actualModuleEntries.map(_.getModule))
    pairByName(expected, actualModuleEntries).foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  private def assertLibraryDependenciesEqual(module: Module)(expected: Seq[dependency[library]]): Unit = {
    val actualLibraryEntries = roots.OrderEnumerator.orderEntries(module).libraryEntries
    assertNamesEqual("Library dependency", expected.map(_.reference), actualLibraryEntries.map(_.getLibrary))
    pairByName(expected, actualLibraryEntries).foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  private def assertDependencyScopeAndExportedFlagEqual(expected: dependency[_], actual: roots.ExportableOrderEntry): Unit = {
    expected.foreach(isExported)(it => assertEquals("Dependency isExported flag", it, actual.isExported))
    expected.foreach(scope)(it => assertEquals("Dependency scope", it, actual.getScope))
  }

  private def assertProjectLibrariesEqual(expectedProject: project): Unit =
    expectedProject.foreach(libraries) { expectedLibraries =>
      val actualLibraries = ProjectLibraryTable.getInstance(getProject).getLibraries.toSeq
      assertNamesEqual("Project library", expectedLibraries, actualLibraries)
      pairByName(expectedLibraries, actualLibraries).foreach((assertLibraryContentsEqual _).tupled)
    }

  private def assertLibraryContentsEqual(expected: library, actual: Library): Unit = {
    expected.foreach(classes)(assertLibraryFilesEqual(actual, roots.OrderRootType.CLASSES))
    expected.foreach(ProjectStructureDsl.sources)(assertLibraryFilesEqual(actual, roots.OrderRootType.SOURCES))
    expected.foreach(javadocs)(assertLibraryFilesEqual(actual, roots.JavadocOrderRootType.getInstance))
  }

  private def assertLibraryFilesEqual(lib: Library, fileType: roots.OrderRootType)(expectedFiles: Seq[String]): Unit =
    // TODO: support non-local library contents (if necessary)
    // This implemetation works well only for local files; *.zip and other archives are not supported
    // @dancingrobot84
    assertMatch("Library file", expectedFiles, lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))))

  private def assertNamesEqual[T](what: String, expected: Seq[Named], actual: Seq[T])(implicit nameOf: HasName[T]): Unit =
    assertMatch(what, expected.map(_.name), actual.map(s => nameOf(s)))

  private def assertEquals[T](what: String, expected: T, actual: T): Unit = {
    if (expected != null && !expected.equals(actual))
      fail(s"$what mismatch\nExpected [ $expected ]\nActual   [ $actual ]")
  }

  private def pairByName[T <: Named, U](fst: Seq[T], snd: Seq[U])(implicit nameOf: HasName[U]): Seq[(T, U)] =
    fst.flatMap(f => snd.find(s => nameOf(s) == f.name).map((f, _)))
}

object ImportingTestCase {
  implicit class RichOrderEnumerator(enumerator: roots.OrderEnumerator) {
    def entries: Seq[roots.OrderEntry] = {
      val processor = new CommonProcessors.CollectProcessor[roots.OrderEntry]
      enumerator.forEach(processor)
      processor.getResults.asScala.toSeq
    }

    def moduleEntries: Seq[roots.ModuleOrderEntry] =
      entries.collect { case e : roots.ModuleOrderEntry => e}

    def libraryEntries: Seq[roots.LibraryOrderEntry] =
      entries.collect { case e : roots.LibraryOrderEntry => e }
  }

  trait HasName[T] {
    def apply(obj: T): String
  }
}

trait InexactMatch {
  self: ImportingTestCase =>
  override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit =
    expected.foreach(it => assertTrue(s"$what mismatch\nExpected [ ${expected.toList} ]\nActual   [ ${actual.toList} ]", actual.contains(it)))
}

trait ExactMatch {
  self: ImportingTestCase =>
  override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit = {
    val errorMessage = s"$what mismatch\nExpected [ ${expected.toList} ]\nActual   [ ${actual.toList} ]"
    assertTrue(errorMessage, expected.forall(actual.contains))
    assertTrue(errorMessage, actual.forall(expected.contains))
  }
}

