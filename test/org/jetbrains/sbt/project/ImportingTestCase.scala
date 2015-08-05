package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore}
import com.intellij.util.PathUtil
import junit.framework.Assert._
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

  def getTestProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def scalaPluginBuildOutputDir: File = new File("../../out/plugin/Scala")

  def getProject: Project = myProject

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
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(getTestProjectDir)
    val systemSettings = SbtSystemSettings.getInstance(myProject)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher/sbt-launch.jar")
    systemSettings.setCustomSbtStructureDir(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher")
  }
}

sealed trait MatchBase {
  self: ImportingTestCase =>

  def assertProjectsEqual(expected: project): Unit = {
    assertProjectNameEquals(expected)
    assertProjectSdkEquals(expected)
    assertProjectLanguageLevelEquals(expected)
    assertProjectModulesEqual(expected)
    assertProjectLibrariesEqual(expected)
  }

  def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit

  private def assertProjectNameEquals(expected: project): Unit =
    expected.attributes.get(name).foreach {
      it => assertEquals(it, getProject.getName)
    }

  private def assertProjectSdkEquals(expected: project): Unit =
    expected.attributes.get(sdk).foreach { it =>
      assertEquals(it, roots.ProjectRootManager.getInstance(getProject).getProjectSdk)
    }

  private def assertProjectLanguageLevelEquals(expected: project): Unit =
    expected.attributes.get(languageLevel).foreach { it =>
      assertEquals(it, roots.LanguageLevelProjectExtension.getInstance(getProject).getLanguageLevel)
    }

  private def assertProjectModulesEqual(expected: project): Unit =
    expected.attributes.get(modules).foreach { expectedModules =>
      val actualModules = ModuleManager.getInstance(getProject).getModules
      assertModuleNamesEqual(expectedModules, actualModules)
      expectedModules.foreach { module =>
        val actualModule = actualModules.find(module.attributes.getOrFail(name) == _.getName)
        assertModulesEqual(module, actualModule.get)
      }
    }

  private def assertModulesEqual(expected: module, actual: Module): Unit = {
    expected.attributes.get(contentRoots).foreach(assertModuleContentRootsEqual(actual))
    expected.attributes.get(ProjectStructureDsl.sources).foreach(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE))
    expected.attributes.get(testSources).foreach(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE))
    expected.attributes.get(resources).foreach(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE))
    expected.attributes.get(testResources).foreach(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE))
    expected.attributes.get(excluded).foreach(assertModuleExcludedFoldersEqual(actual))
    expected.attributes.get(moduleDependencies).foreach(assertModuleDependenciesEqual(actual))
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String]): Unit = {
    val expectedRoots = expected.map(VfsUtilCore.pathToUrl)
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl)
    assertMatch(expectedRoots, actualRoots)
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
    assertMatch(expected, actualFolders)
  }

  private def getSingleContentRoot(module: Module): roots.ContentEntry = {
    val contentRoots = roots.ModuleRootManager.getInstance(module).getContentEntries
    assertEquals(s"Expected single content root, Got: $contentRoots", 1, contentRoots.length)
    contentRoots.head
  }

  private def assertModuleDependenciesEqual(module: Module)(expected: Seq[module]): Unit =
    assertModuleNamesEqual(expected, roots.ModuleRootManager.getInstance(module).getModuleDependencies)

  private def assertModuleNamesEqual(expected: Seq[module], actual: Seq[Module]): Unit = {
    val actualNames = actual.map(_.getName)
    val expectedNames = expected.map(_.attributes.getOrFail(name))
    assertMatch(expectedNames, actualNames)
  }

  private def assertProjectLibrariesEqual(expectedProject: project): Unit =
    expectedProject.attributes.get(libraries).foreach { expectedLibraries =>
      val actualLibraries = ProjectLibraryTable.getInstance(getProject).getLibraries
      assertLibraryNamesEqual(expectedLibraries, actualLibraries)
      expectedLibraries.foreach { library =>
        val actualLibrary = actualLibraries.find(_.getName == library.attributes.getOrFail(name))
        assertLibraryContentsEqual(library, actualLibrary.get)
      }
    }

  private def assertLibraryNamesEqual(expected: Seq[library], actual: Seq[Library]): Unit = {
    val actualNames = actual.map(_.getName)
    val expectedNames = expected.map(_.attributes.getOrFail(name))
    assertMatch(expectedNames, actualNames)
  }

  private def assertLibraryContentsEqual(expected: library, actual: Library): Unit = {
    expected.attributes.get(classes).foreach(assertLibraryFilesEqual(actual, OrderRootType.CLASSES))
    expected.attributes.get(ProjectStructureDsl.sources).foreach(assertLibraryFilesEqual(actual, OrderRootType.SOURCES))
    expected.attributes.get(javadocs).foreach(assertLibraryFilesEqual(actual, JavadocOrderRootType.getInstance))
  }

  private def assertLibraryFilesEqual(lib: Library, fileType: OrderRootType)(expectedFiles: Seq[String]): Unit =
    // TODO: support non-local library contents (if necessary)
    // This implemetation works well only for local files; *.zip and other archives are not supported
    // @dancingrobot84
    assertMatch(expectedFiles, lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))))
}

trait InexactMatch extends MatchBase {
  self: ImportingTestCase =>
  override def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit =
    expected.foreach(it => assertTrue(s"$actual does not contain '$it'", actual.contains(it)))
}

trait ExactMatch extends MatchBase {
  self: ImportingTestCase =>
  override def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit = {
    val errorMessage = s"Expected: $expected, Got: $actual"
    assertTrue(errorMessage, expected.forall(actual.contains))
    assertTrue(errorMessage, actual.forall(expected.contains))
  }
}

