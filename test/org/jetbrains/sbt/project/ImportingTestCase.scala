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

  def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit

  def getTestProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def scalaPluginBuildOutputDir: File = new File("../../out/plugin/Scala")

  def getProject: Project = myProject

  def assertProjectsEqual(expected: project): Unit = {
    assertEquals(expected.name, getProject.getName)
    assertProjectSdkEquals(expected)
    assertProjectLanguageLevelEquals(expected)
    assertProjectModulesEqual(expected)
    assertProjectLibrariesEqual(expected)
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
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(getTestProjectDir)
    val systemSettings = SbtSystemSettings.getInstance(myProject)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher/sbt-launch.jar")
    systemSettings.setCustomSbtStructureDir(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher")
  }

  private def assertProjectSdkEquals(expected: project): Unit =
    expected.foreach(sdk)(it => assertEquals(it, roots.ProjectRootManager.getInstance(getProject).getProjectSdk))

  private def assertProjectLanguageLevelEquals(expected: project): Unit =
    expected.foreach(languageLevel)(it => assertEquals(it, roots.LanguageLevelProjectExtension.getInstance(getProject).getLanguageLevel))

  private def assertProjectModulesEqual(expected: project): Unit =
    expected.foreach(modules) { expectedModules =>
      val actualModules = ModuleManager.getInstance(getProject).getModules
      assertNamesEqual(expectedModules, actualModules)
      expectedModules.foreach { module =>
        val actualModule = actualModules.find(module.name == _.getName)
        assertModulesEqual(module, actualModule.get)
      }
    }

  private def assertModulesEqual(expected: module, actual: Module): Unit = {
    expected.foreach(contentRoots)(assertModuleContentRootsEqual(actual))
    expected.foreach(ProjectStructureDsl.sources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE))
    expected.foreach(testSources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE))
    expected.foreach(resources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE))
    expected.foreach(testResources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE))
    expected.foreach(excluded)(assertModuleExcludedFoldersEqual(actual))
    expected.foreach(moduleDependencies)(assertModuleDependenciesEqual(actual))
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
    assertNamesEqual(expected, roots.ModuleRootManager.getInstance(module).getModuleDependencies)

  private def assertProjectLibrariesEqual(expectedProject: project): Unit =
    expectedProject.foreach(libraries) { expectedLibraries =>
      val actualLibraries = ProjectLibraryTable.getInstance(getProject).getLibraries
      assertNamesEqual(expectedLibraries, actualLibraries)
      expectedLibraries.foreach { library =>
        val actualLibrary = actualLibraries.find(_.getName == library.name)
        assertLibraryContentsEqual(library, actualLibrary.get)
      }
    }

  private def assertLibraryContentsEqual(expected: library, actual: Library): Unit = {
    expected.foreach(classes)(assertLibraryFilesEqual(actual, OrderRootType.CLASSES))
    expected.foreach(ProjectStructureDsl.sources)(assertLibraryFilesEqual(actual, OrderRootType.SOURCES))
    expected.foreach(javadocs)(assertLibraryFilesEqual(actual, JavadocOrderRootType.getInstance))
  }

  private def assertNamesEqual(expected: Seq[{def name: String}], actual: Seq[{def getName(): String}]): Unit =
    assertMatch(expected.map(_.name), actual.map(_.getName()))

  private def assertLibraryFilesEqual(lib: Library, fileType: OrderRootType)(expectedFiles: Seq[String]): Unit =
    // TODO: support non-local library contents (if necessary)
    // This implemetation works well only for local files; *.zip and other archives are not supported
    // @dancingrobot84
    assertMatch(expectedFiles, lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))))
}

trait InexactMatch {
  self: ImportingTestCase =>
  override def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit =
    expected.foreach(it => assertTrue(s"$actual does not contain '$it'", actual.contains(it)))
}

trait ExactMatch {
  self: ImportingTestCase =>
  override def assertMatch[T](expected: Seq[T], actual: Seq[T]): Unit = {
    val errorMessage = s"Expected: $expected, Got: $actual"
    assertTrue(errorMessage, expected.forall(actual.contains))
    assertTrue(errorMessage, actual.forall(expected.contains))
  }
}

