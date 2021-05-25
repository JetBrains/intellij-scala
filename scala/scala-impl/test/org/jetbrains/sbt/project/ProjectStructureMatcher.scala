package org.jetbrains.sbt
package project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModuleOrderEntry, ModuleRootManager}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.{CommonProcessors, PathUtil}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.project.external.{SdkReference, SdkUtils}
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaLibraryProperties}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.data.SbtModuleData
import org.junit.Assert
import org.junit.Assert.{assertFalse, assertTrue, fail}

import scala.jdk.CollectionConverters._

trait ProjectStructureMatcher {

  import ProjectStructureMatcher._

  def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit
  def assertMatchUnordered[T : Ordering](what: String, expected: Seq[T], actual: Seq[T]): Unit =
    assertMatch(what, expected.sorted, actual.sorted)

  def assertProjectsEqual(expected: project, actual: Project): Unit = {
    assertEquals("Project name", expected.name, actual.getName)
    expected.foreach(sdk)(assertProjectSdkEqual(actual))
    expected.foreach(modules)(assertProjectModulesEqual(actual))
    expected.foreach(libraries)(assertProjectLibrariesEqual(actual))

    expected.foreach(javaLanguageLevel)(assertProjectJavaLanguageLevel(actual))
    expected.foreach(javaTargetBytecodeLevel)(assertProjectJavaTargetBytecodeLevel(actual))
    expected.foreach(javacOptions)(assertProjectJavacOptions(actual))
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

  private def assertProjectSdkEqual(project: Project)(expectedSdkRef: SdkReference): Unit = {
    val expectedSdk = SdkUtils.findProjectSdk(expectedSdkRef).getOrElse {
      fail(s"Sdk $expectedSdkRef nof found").asInstanceOf[Nothing]
    }
    val actualSdk = roots.ProjectRootManager.getInstance(project).getProjectSdk
    assertEquals("Project SDK", expectedSdk, actualSdk)
  }

  private def assertProjectModulesEqual(project: Project)(expectedModules: Seq[module]): Unit = {
    val actualModules = ModuleManager.getInstance(project).getModules.toSeq
    assertNamesEqual("Project module", expectedModules, actualModules)
    pairModules(expectedModules, actualModules).foreach((assertModulesEqual _).tupled)
  }

  private def assertModulesEqual(expected: module, actual: Module): Unit = {
    import ProjectStructureDsl._

    expected.foreach(contentRoots)(assertModuleContentRootsEqual(actual))
    expected.foreach(sources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE))
    expected.foreach(testSources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE))
    expected.foreach(resources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE))
    expected.foreach(testResources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE))
    expected.foreach(excluded)(assertModuleExcludedFoldersEqual(actual))
    expected.foreach(moduleDependencies)(assertModuleDependenciesEqual(actual))
    expected.foreach(libraryDependencies)(assertLibraryDependenciesEqual(actual))
    expected.foreach(libraries)(assertModuleLibrariesEqual(actual))

    expected.foreach(javaLanguageLevel)(assertModuleJavaLanguageLevel(actual))
    expected.foreach(javaTargetBytecodeLevel)(assertModuleJavaTargetBytecodeLevel(actual))
    expected.foreach(javacOptions)(assertModuleJavacOptions(actual))

    assertGroupEqual(expected, actual)
  }

  protected def assertModuleJavaLanguageLevel(module: Module)(expected: LanguageLevel): Unit = {
    val settings = ModuleRootManager.getInstance(module).getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
    val actual = settings.getLanguageLevel
    Assert.assertEquals(s"Module java language level (${module.getName})", expected, actual)
  }

  protected def assertModuleJavaTargetBytecodeLevel(module: Module)(expected: String): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(module.getProject)
    val actual = compilerSettings.getBytecodeTargetLevel(module)
    Assert.assertEquals(s"Module java target bytecode level (${module.getName})", expected, actual)
  }

  private def assertProjectJavaLanguageLevel(project: Project)(expected: LanguageLevel): Unit = {
    val settings = roots.LanguageLevelProjectExtension.getInstance(project)
    val actual = settings.getLanguageLevel
    assertEquals("Project java language level", expected, actual)
  }

  private def assertProjectJavaTargetBytecodeLevel(project: Project)(expected: String): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(project)
    val actual = compilerSettings.getProjectBytecodeTarget
    assertEquals("Project target bytecode level (for Java sources)", expected, actual)
  }

  protected def assertModuleJavacOptions(module: Module)(expectedOptions: Seq[String]): Unit = {
    val settings = CompilerConfiguration.getInstance(module.getProject)
    val actual = settings.getAdditionalOptions(module).asScala
    Assert.assertEquals(s"Module javacOptions (${module.getName})", expectedOptions, actual)
  }
  private def assertProjectJavacOptions(project: Project)(expectedOptions: Seq[String]): Unit = {
    val settings = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    val actual = settings.ADDITIONAL_OPTIONS_STRING
    assertEquals("Project javacOptions", expectedOptions.mkString(" "), actual)
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String]): Unit = {
    val expectedRoots = expected.map(VfsUtilCore.pathToUrl)
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl).toSeq
    assertMatch("Content root", expectedRoots, actualRoots)
  }

  private def assertModuleContentFoldersEqual(module: Module, folderType: JpsModuleSourceRootType[_])(expected: Seq[String]): Unit = {
    val contentRoot = getSingleContentRoot(module)
    assertContentRootFoldersEqual(contentRoot, contentRoot.getSourceFolders(folderType).asScala.toSeq, expected)
  }

  private def assertModuleExcludedFoldersEqual(module: Module)(expected: Seq[String]): Unit = {
    val contentRoot = getSingleContentRoot(module)
    assertContentRootFoldersEqual(contentRoot, contentRoot.getExcludeFolders.toSeq, expected)
  }

  private def assertContentRootFoldersEqual(contentRoot: roots.ContentEntry, actual: Seq[roots.ContentFolder], expected: Seq[String]): Unit = {
    val actualFolders = actual.map { folder =>
      val folderUrl = folder.getUrl
      if (folderUrl.startsWith(contentRoot.getUrl))
        folderUrl.substring(Math.min(folderUrl.length, contentRoot.getUrl.length + 1))
      else
        folderUrl
    }
    assertMatchUnordered("Content folder", expected, actualFolders)
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
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertLibraryContentsEqual(expected: library, actual: Library): Unit = {
    expected.foreach(classes)(assertLibraryFilesEqual(actual, roots.OrderRootType.CLASSES))
    expected.foreach(sources)(assertLibraryFilesEqual(actual, roots.OrderRootType.SOURCES))
    expected.foreach(javadocs)(assertLibraryFilesEqual(actual, roots.JavadocOrderRootType.getInstance))
  }

  // TODO: support non-local library contents (if necessary)
  // This implementation works well only for local files; *.zip and other archives are not supported
  // @dancingrobot84
  private def assertLibraryFilesEqual(lib: Library, fileType: roots.OrderRootType)(expectedFiles: Seq[String]): Unit = {
    val expectedNormalized = expectedFiles.map(normalizePathSeparators)
    val actualNormalised = lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))).toSeq.map(normalizePathSeparators)
    assertMatch("Library file", expectedNormalized, actualNormalised)
  }

  private def normalizePathSeparators(path: String): String = path.replace("\\", "/")

  private def assertLibraryScalaSdk(expected: library, actual0: Library): Unit = {
    import org.jetbrains.plugins.scala.project.LibraryExExt
    val actual = actual0.asInstanceOf[LibraryEx]
    expected.foreach(scalaSdkSettings) {
      case None =>
        assertFalse(s"Scala library shouldn't be marked as Scala SDK: ${actual.getName}", actual.isScalaSdk)
        assertFalse(s"Scala library shouldn't contain Scala SDK properties ${actual.getName}", actual.getProperties.isInstanceOf[ScalaLibraryProperties])
      case Some(expectedScalaSdk) =>
        assertTrue(s"Scala library should be marked as Scala SDK: ${actual.getName}", actual.isScalaSdk)
        val sdkProperties = actual.properties
        assertEquals("Scala SDK language level", expectedScalaSdk.languageLevel, sdkProperties.languageLevel)

        val expectedClassPath = expectedScalaSdk.classpath.map(normalizePathSeparators).sorted.mkString("\n")
        val actualClasspath = sdkProperties.compilerClasspath.map(_.getAbsolutePath).map(normalizePathSeparators).sorted.mkString("\n")
        assertEquals("Scala SDK classpath", expectedClassPath, actualClasspath)
    }
  }

  private def assertModuleLibrariesEqual(module: Module)(expectedLibraries: Seq[library]): Unit = {
    val actualLibraries = roots.OrderEnumerator.orderEntries(module).libraryEntries.filter(_.isModuleLevel).map(_.getLibrary)
    assertNamesEqual("Module library", expectedLibraries, actualLibraries)
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertNamesEqual[T](what: String, expected: Seq[Named], actual: Seq[T])(implicit nameOf: HasName[T]): Unit =
    assertMatchUnordered(what, expected.map(_.name), actual.map(s => nameOf(s)))

  private def assertGroupEqual[T](expected: module, actual: Module): Unit = {
    val actualPath = ModuleManager.getInstance(actual.getProject).getModuleGroupPath(actual)

    if (expected.group == null) Assert.assertNull(actualPath)
    else {
      Assert.assertNotNull(actualPath)
      Assert.assertEquals("Wrong module group path", expected.group.toSeq, actualPath.toSeq)
    }
  }

  private def assertEquals[T](what: String, expected: T, actual: T): Unit = {
    org.junit.Assert.assertEquals(s"$what mismatch", expected, actual)
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
    expected.foreach { it =>
      assertTrue(
        s"""$what mismatch (should contain at least '$it')
           |Expected [ ${expected.toList} ]
           |Actual   [ ${actual.toList} ]""".stripMargin,
        actual.contains(it)
      )
    }
}

trait ExactMatch {
  self: ProjectStructureMatcher =>
  override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit = {
    Assert.assertEquals(s"$what mismatch", expected, actual)
  }
}

