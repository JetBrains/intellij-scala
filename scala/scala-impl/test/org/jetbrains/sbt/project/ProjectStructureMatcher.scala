package org.jetbrains.sbt
package project

import java.util
import java.util.Arrays

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.{CommonProcessors, PathUtil}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.data.SbtModuleData
import org.junit.Assert
import org.junit.Assert.{assertTrue, fail}

import scala.jdk.CollectionConverters._

trait ProjectStructureMatcher {

  import ProjectStructureMatcher._

  def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit

  def assertProjectsEqual(expected: project, actual: Project): Unit = {
    assertEquals("Project name", expected.name, actual.getName)
    expected.foreach(sdk)(it => assertEquals("Project SDK", it, roots.ProjectRootManager.getInstance(actual).getProjectSdk))
    expected.foreach(languageLevel)(it => assertEquals("Project language level", it, roots.LanguageLevelProjectExtension.getInstance(actual).getLanguageLevel))
    expected.foreach(modules)(assertProjectModulesEqual(actual))
    expected.foreach(libraries)(assertProjectLibrariesEqual(actual))
  }

  private implicit def namedImplicit[T <: Named]: HasName[T] =
    (named: Named) => named.name

  private implicit val ideaModuleNameImplicit: HasName[Module] =
    (module: Module) => module.getName

  private implicit val ideaLibraryNameImplicit: HasName[Library] =
    (library: Library) => library.getName

  private implicit val ideaModuleEntryNameImplicit: HasName[roots.ModuleOrderEntry] =
    (entry: roots.ModuleOrderEntry) => entry.getModuleName

  private implicit val ideaLibraryEntryNameImplicit: HasName[roots.LibraryOrderEntry] =
    (entry: roots.LibraryOrderEntry) => entry.getLibraryName

  private implicit val ideaModuleImplicit: HasModule[Module] =
    (module: Module) => module

  private implicit val ideaModuleEntryModuleImplicit: HasModule[ModuleOrderEntry] =
    (entry: roots.ModuleOrderEntry) => entry.getModule

  private def assertProjectModulesEqual(project: Project)(expectedModules: Seq[module]): Unit = {
    val actualModules = ModuleManager.getInstance(project).getModules.toSeq
    assertNamesEqual("Project module", expectedModules, actualModules)
    pairModules(expectedModules, actualModules).foreach((assertModulesEqual _).tupled)
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
    expected.foreach(libraries)(assertModuleLibrariesEqual(actual))

    assertGroupEqual(expected, actual)
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String]): Unit = {
    val expectedRoots = expected.map(VfsUtilCore.pathToUrl)
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl)
    assertMatch("Content root", expectedRoots, actualRoots)
  }

  private def assertModuleContentFoldersEqual(module: Module, folderType: JpsModuleSourceRootType[_])(expected: Seq[String]): Unit = {
    val contentRoot = getSingleContentRoot(module)
    assertContentRootFoldersEqual(contentRoot, contentRoot.getSourceFolders(folderType).asScala.toSeq, expected)
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
    val paired = pairModules(expected, actualModuleEntries)
    paired.foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
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

  private def assertProjectLibrariesEqual(project: Project)(expectedLibraries: Seq[library]): Unit = {
    val actualLibraries = project.libraries
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
  // This implementation works well only for local files; *.zip and other archives are not supported
  // @dancingrobot84
    assertMatch("Library file", expectedFiles, lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))))

  private def assertModuleLibrariesEqual(module: Module)(expectedLibraries: Seq[library]): Unit = {
    val actualLibraries = roots.OrderEnumerator.orderEntries(module).libraryEntries.filter(_.isModuleLevel).map(_.getLibrary)
    assertNamesEqual("Module library", expectedLibraries, actualLibraries)
    pairByName(expectedLibraries, actualLibraries).foreach((assertLibraryContentsEqual _).tupled)
  }

  private def assertNamesEqual[T](what: String, expected: Seq[Named], actual: Seq[T])(implicit nameOf: HasName[T]): Unit =
    assertMatch(what, expected.map(_.name), actual.map(s => nameOf(s)))

  private def assertGroupEqual[T](expected: module, actual: Module): Unit = {
    val actualPath = ModuleManager.getInstance(actual.getProject).getModuleGroupPath(actual)

    if (expected.group == null) Assert.assertNull(actualPath)
    else {
      Assert.assertNotNull(actualPath)
      Assert.assertEquals("Wrong module group path", expected.group.toSeq, actualPath.toSeq)
    }
  }

  private def assertEquals[T](what: String, expected: T, actual: T): Unit = {
    if (expected != null && !expected.equals(actual))
      fail(s"$what mismatch\nExpected [ $expected ]\nActual [ $actual ]")
  }

  /**
    * this hack should make old tests work unchanged and as expected.
    * when module names can be duplicate, need to use more specialized code that will only work for sbt
    * (the test dsl is also used for gradle importing tests)
    */
  private def pairModules[T <: Attributed, U](expected: Seq[T], actual: Seq[U])(implicit nameOfT: HasName[T], nameOfU: HasName[U], moduleOf: HasModule[U]) =
    if (expected.map(nameOfT.apply).distinct.length == expected.length)
      pairByName(expected, actual)
    else
      pairBySbtId(expected, actual)

  private def pairByName[T, U](expected: Seq[T], actual: Seq[U])(implicit nameOfT: HasName[T], nameOfU: HasName[U]): Seq[(T, U)] =
    expected.flatMap(e => actual.find(a => nameOfU(a) == nameOfT(e)).map((e, _)))

  private def pairBySbtId[T <: Attributed, U](expected: Seq[T], actual: Seq[U])(implicit moduleOf: HasModule[U]): Seq[(T, U)] = {
    expected.flatMap { e =>
      val uri = e.get(sbtBuildURI).get
      val id = e.get(sbtProjectId).get
      actual.find { m =>
        SbtUtil.getSbtModuleData(moduleOf(m)) match {
          case Some(SbtModuleData(projectId, buildURI)) =>
            projectId == id && buildURI == uri
          case _ => false
        }
      }
        .map((e,_))
        .toIterable
    }
  }
}

object ProjectStructureMatcher {
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

  trait HasModule[T] {
    def apply(t: T): Module
  }
}

trait InexactMatch {
  self: ProjectStructureMatcher =>
  override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit =
    expected.foreach(it => assertTrue(s"$what mismatch\nExpected [ ${expected.toList} ]\nActual   [ ${actual.toList} ]", actual.contains(it)))
}

trait ExactMatch {
  self: ProjectStructureMatcher =>
  override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit = {
    val errorMessage = s"$what mismatch\nExpected [ ${expected.toList} ]\nActual   [ ${actual.toList} ]"
    assertTrue(errorMessage, expected.forall(actual.contains))
    assertTrue(errorMessage, actual.forall(expected.contains))
  }
}

