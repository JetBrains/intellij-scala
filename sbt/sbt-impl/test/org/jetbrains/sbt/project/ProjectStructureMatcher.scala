package org.jetbrains.sbt
package project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{ContentEntry, LanguageLevelModuleExtensionImpl, LibraryOrderEntry, ModuleRootManager}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.{CommonProcessors, PathUtil}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.external.{SdkReference, SdkUtils, ShownNotification, ShownNotificationsKey}
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ProjectExt, ScalaLibraryProperties}
import org.jetbrains.sbt.DslUtils.MatchType
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.AttributeMatchType
import org.junit.Assert.{assertFalse, assertTrue, fail}
import org.junit.{Assert, ComparisonFailure}

import java.io.File
import scala.jdk.CollectionConverters._

trait ProjectStructureMatcher {

  import ProjectStructureMatcher._

  protected def defaultAssertMatch: AttributeMatchType

  final def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T])
                          (mt: Option[DslUtils.MatchType]): Unit = {
    val matcher = mt.map(convertMatchType).getOrElse(defaultAssertMatch)
    matcher.assertMatch(what, expected, actual)
  }

  def assertMatchWithIgnoredOrder[T : Ordering](what: String, expected: Seq[T], actual: Seq[T])
                                               (mt: Option[DslUtils.MatchType]): Unit =
    assertMatch(what, expected.sorted, actual.sorted)(mt)

  def assertProjectsEqual(expected: project, actual: Project)(implicit compareOptions: ProjectComparisonOptions): Unit = {
    assertEquals("Project name", expected.name, actual.getName)
    expected.foreach0(sdk)(assertProjectSdkEqual(actual))
    expected.foreach(libraries)(assertProjectLibrariesEqual(actual))
    expected.foreach0(javaLanguageLevel)(assertProjectJavaLanguageLevel(actual))
    expected.foreach0(javaTargetBytecodeLevel)(assertProjectJavaTargetBytecodeLevel(actual))
    expected.foreach0(javacOptions)(assertProjectJavacOptions(actual))

    expected.foreach(modules)(assertProjectModulesEqual(actual)(_))
    expected.foreach(packagePrefix)(assertPackagePrefixEqual(actual)(_))
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

  private def assertProjectSdkEqual(project: Project)(expectedSdkRef: SdkReference): Unit = {
    val expectedSdk = SdkUtils.findProjectSdk(expectedSdkRef).getOrElse {
      fail(s"Sdk $expectedSdkRef nof found").asInstanceOf[Nothing]
    }
    val actualSdk = roots.ProjectRootManager.getInstance(project).getProjectSdk
    assertEquals("Project SDK", expectedSdk, actualSdk)
  }

  private def assertProjectModulesEqual(project: Project)
                                       (expectedModules: Seq[module])(mt: Option[MatchType])
                                       (implicit compareOptions: ProjectComparisonOptions): Unit = {
    val actualModulesAll = ModuleManager.getInstance(project).getModules.toSeq
    val actualModules = if (compareOptions.strictCheckForBuildModules || expectedModules.exists(_.isBuildModule))
      actualModulesAll
    else
      actualModulesAll.filterNot(_.isBuildModule)
    assertNamesEqualIgnoreOrder("Project module", expectedModules, actualModules)(mt)
    val pairs = pairModules(expectedModules, actualModules)
    pairs.foreach((assertModulesEqual _).tupled)
  }

  private def assertModulesEqual(expected: module, actual: Module): Unit = {
    import ProjectStructureDsl._

    expected.foreach(contentRoots)(assertModuleContentRootsEqual(actual))
    expected.foreach(sources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE, "Sources"))
    expected.foreach(testSources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE, "Test sources"))
    expected.foreach(resources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE, "Resources"))
    expected.foreach(testResources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE, "Test resources"))
    expected.foreach(excluded)(assertModuleExcludedFoldersEqual(actual))
    expected.foreach(moduleDependencies)(assertModuleDependenciesEqual(actual))
    expected.foreach(libraryDependencies)(assertLibraryDependenciesEqual(actual))
    expected.foreach(libraries)(assertModuleLibrariesEqual(actual))

    expected.foreach0(javaLanguageLevel)(assertModuleJavaLanguageLevel(actual))
    expected.foreach0(javaTargetBytecodeLevel)(assertModuleJavaTargetBytecodeLevel(actual))
    expected.foreach(javacOptions)(assertModuleJavacOptions(actual))
    expected.foreach0(compileOrder)(assertModuleCompileOrder(actual))

    lazy val sbtModuleData = SbtUtil.getSbtModuleData(actual).getOrElse {
      fail(s"Can't get module data for module: $actual (${actual.getModuleFilePath})").asInstanceOf[Nothing]
    }
    expected.foreach(sbtBuildURI)(buildURI => _ => {
      assertEquals(s"SBT build URI (module $actual)", buildURI, sbtModuleData.buildURI.uri)
    })
    expected.foreach(sbtProjectId)(projectId => _ => {
      assertEquals(s"SBT project module id (module $actual)", projectId, sbtModuleData.id)
    })
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

  protected def assertModuleJavacOptions(module: Module)(expectedOptions: Seq[String])(mt: Option[MatchType]): Unit = {
    val settings = CompilerConfiguration.getInstance(module.getProject)
    val actual = settings.getAdditionalOptions(module).asScala
    Assert.assertEquals(s"Module javacOptions (${module.getName})", expectedOptions, actual)
  }

  private def assertModuleCompileOrder(module: Module)(expected: CompileOrder): Unit = {
    assertEquals("Compile order", expected, module.scalaCompilerSettings.compileOrder)
  }

  private def assertProjectJavacOptions(project: Project)(expectedOptions: Seq[String]): Unit = {
    val settings = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    val actual = settings.ADDITIONAL_OPTIONS_STRING
    assertEquals("Project javacOptions", expectedOptions.mkString(" "), actual)
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String])(mt: Option[MatchType]): Unit = {
    val expectedRoots = expected.map(VfsUtilCore.pathToUrl)
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl).toSeq
    assertMatch(s"Content root of module `${module.getName}`", expectedRoots, actualRoots)(mt)
  }

  private def assertModuleContentFoldersEqual(module: Module, folderType: JpsModuleSourceRootType[_], folderTypeDisplayName: String)(expected: Seq[String])
                                             (mt: Option[MatchType]): Unit = {
    if (expected.isEmpty) {
      val contentRoots = getContentRoots(module)
      val sourceFolders = contentRoots.flatMap(_.getSourceFolders(folderType).asScala.toSeq).map(_.getUrl)
      assertMatchWithIgnoredOrder(s"$folderTypeDisplayName of module '${module.getName}'", Nil, sourceFolders)(mt)
    }
    else {
      val contentRoot = getSingleContentRoot(module)
      val sourceFolders = contentRoot.getSourceFolders(folderType).asScala.toSeq
      assertContentRootFoldersEqual(folderTypeDisplayName, module, contentRoot, sourceFolders, expected)(mt)
    }
  }

  private def assertModuleExcludedFoldersEqual(module: Module)(expected: Seq[String])(mt: Option[MatchType]): Unit = {
    if (expected.isEmpty) {
      val contentRoots = getContentRoots(module)
      val excludedFolderFiles = contentRoots.flatMap(_.getExcludeFolderFiles).map(_.getUrl)
      assertMatchWithIgnoredOrder(s"Excluded folders of module '${module.getName}'", Nil, excludedFolderFiles)(mt)
    }
    else {
      val contentRoot = getSingleContentRoot(module)
      assertContentRootFoldersEqual(s"Excluded folders", module, contentRoot, contentRoot.getExcludeFolders.toSeq, expected)(mt)
    }
  }

  private def assertContentRootFoldersEqual(folderType: String, module: Module, contentRoot: roots.ContentEntry, actual: Seq[roots.ContentFolder], expected: Seq[String])
                                           (mt: Option[MatchType]): Unit = {
    val actualFolders = actual.map { folder =>
      val folderUrl = folder.getUrl
      if (folderUrl.startsWith(contentRoot.getUrl))
        folderUrl.substring(Math.min(folderUrl.length, contentRoot.getUrl.length + 1))
      else
        folderUrl
    }
    assertMatchWithIgnoredOrder(s"$folderType of module '${module.getName}'", expected, actualFolders)(mt)
  }

  private def getSingleContentRoot(module: Module): roots.ContentEntry = {
    val contentRoots = getContentRoots(module)
    assertEquals(s"Expected single content root in module ${module.getName}, Got: $contentRoots", 1, contentRoots.length)
    contentRoots.head
  }

  private def getContentRoots(module: Module): Seq[ContentEntry] =
    roots.ModuleRootManager.getInstance(module).getContentEntries.toSeq

  private def assertPackagePrefixEqual(project: Project)(expectedPrefix: String)(mt: Option[MatchType]): Unit = {
    project.modules.filterNot(_.isBuildModule).foreach { module =>
      val contentRoot = getSingleContentRoot(module)
      contentRoot.getSourceFolders.foreach { source =>
        assertEquals(s"package prefix for source folder $source of module `${module.getName}`", expectedPrefix, source.getPackagePrefix)
      }
    }
  }

  private def assertModuleDependenciesEqual(module: Module)(expected: Seq[dependency[module]])(mt: Option[MatchType]): Unit = {
    val actualModuleEntries = roots.OrderEnumerator.orderEntries(module).moduleEntries
    assertNamesEqualIgnoreOrder(s"Module dependency of module `${module.getName}`", expected.map(_.reference), actualModuleEntries.map(_.getModule))(mt)
    val paired = pairModules(expected, actualModuleEntries)
    paired.foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  private def assertLibraryDependenciesEqual(module: Module)(expected: Seq[dependency[library]])(mt: Option[MatchType]): Unit = {
    val actualLibraryEntries = roots.OrderEnumerator.orderEntries(module).libraryEntries
    assertNamesEqualIgnoreOrder(s"Library dependency of module `${module.getName}`", expected.map(_.reference), actualLibraryEntries.map(_.getLibrary))(mt)
    assertUnmanagedLibraryIsAboveOtherLibrariesIfExists(actualLibraryEntries)
    pairByName(expected, actualLibraryEntries).foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  private def assertUnmanagedLibraryIsAboveOtherLibrariesIfExists(actual: Seq[LibraryOrderEntry]): Unit = {
    val librariesWithoutScalaSDK = actual.map(_.getLibrary).filterNot(_.isScalaSdk)
    val index = librariesWithoutScalaSDK.indexWhere(_.getName == s"sbt: ${Sbt.UnmanagedLibraryName}")
    assert(index == 0 || index == -1, "Library for unmanaged jars exists, but it is not the highest in the order")
  }

  private def assertDependencyScopeAndExportedFlagEqual(expected: dependency[_], actual: roots.ExportableOrderEntry): Unit = {
    expected.foreach0(isExported)(it => assertEquals("Dependency isExported flag", it, actual.isExported))
    expected.foreach0(scope)(it => assertEquals("Dependency scope", it, actual.getScope))
  }

  private def assertProjectLibrariesEqual(project: Project)(expectedLibraries: Seq[library])(mt: Option[MatchType]): Unit = {
    val actualLibraries = project.libraries
    assertNamesEqualIgnoreOrder("Project library", expectedLibraries, actualLibraries)(mt)
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertLibraryContentsEqual(expected: library, actual: Library): Unit = {
    expected.foreach(libClasses)(assertLibraryFilesEqual(actual, roots.OrderRootType.CLASSES))
    expected.foreach(libSources)(assertLibraryFilesEqual(actual, roots.OrderRootType.SOURCES))
    expected.foreach(libJavadocs)(assertLibraryFilesEqual(actual, roots.JavadocOrderRootType.getInstance))
  }

  // TODO: support non-local library contents (if necessary)
  // This implementation works well only for local files; *.zip and other archives are not supported
  // @dancingrobot84
  private def assertLibraryFilesEqual(lib: Library, fileType: roots.OrderRootType)(expectedFiles: Seq[String])(mt: Option[MatchType]): Unit = {
    val expectedNormalized = expectedFiles.map(normalizePathSeparators)
    val actualNormalised = lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))).toSeq.map(normalizePathSeparators)
    assertMatch("Library file", expectedNormalized, actualNormalised)(mt)
  }

  private def normalizePathSeparators(path: String): String = path.replace("\\", "/")

  private def assertLibraryScalaSdk(expectedLibrary: library, actualLibrary0: Library): Unit = {
    import org.jetbrains.plugins.scala.project.LibraryExExt
    val actualLibrary = actualLibrary0.asInstanceOf[LibraryEx]
    expectedLibrary.foreach0(scalaSdkSettings) {
      case None =>
        assertFalse(s"Scala library should NOT be marked as Scala SDK: ${actualLibrary.getName}", actualLibrary.isScalaSdk)
        assertFalse(s"Scala library should NOT contain Scala SDK properties ${actualLibrary.getName}", actualLibrary.getProperties.isInstanceOf[ScalaLibraryProperties])
      case Some(expectedScalaSdk) =>
        assertTrue(s"Scala library should be marked as Scala SDK: ${actualLibrary.getName}", actualLibrary.isScalaSdk)
        val sdkProperties = actualLibrary.properties
        assertEquals("Scala SDK language level", expectedScalaSdk.languageLevel, sdkProperties.languageLevel)

        def testClasspath(name: String, expectedClasspathStr: Seq[String], actualClasspathFile: Seq[File]): Unit = {
          val expectedClassPathNorm = expectedClasspathStr.map(normalizePathSeparators).sorted.mkString("\n")
          val actualClasspathNorm = actualClasspathFile.map(_.getAbsolutePath).map(normalizePathSeparators).sorted.mkString("\n")
          assertEquals(name, expectedClassPathNorm, actualClasspathNorm)
        }

        expectedScalaSdk.classpath.foreach(testClasspath("Scala SDK classpath", _, sdkProperties.compilerClasspath))
        expectedScalaSdk.extraClasspath.foreach(testClasspath("Scala SDK extra classpath", _, sdkProperties.scaladocExtraClasspath))
    }
  }

  private def assertModuleLibrariesEqual(module: Module)(expectedLibraries: Seq[library])(mt: Option[MatchType]): Unit = {
    val actualLibraries = roots.OrderEnumerator.orderEntries(module).libraryEntries.filter(_.isModuleLevel).map(_.getLibrary)
    assertNamesEqualIgnoreOrder("Module library", expectedLibraries, actualLibraries)(mt)
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertNamesEqualIgnoreOrder[T](what: String, expected: Seq[Named], actual: Seq[T])
                                            (mt: Option[MatchType])
                                            (implicit nameOf: HasName[T]): Unit =
    assertMatchWithIgnoredOrder(what, expected.map(_.name), actual.map(s => nameOf(s)))(mt)

//  private def assertGroupEqual[T](expected: module, actual: Module): Unit = {
//    val actualPath: Array[String] =
//      ModuleManager.getInstance(actual.getProject).getModuleGroupPath(actual)
//
//    assertCollectionEquals(
//      s"Wrong module group path for module `${actual.getName}`",
//      if (expected.group != null) expected.group.toSeq else null,
//      if (actualPath != null) actualPath.toSeq else null
//    )
//  }

  private def assertEquals[T](what: String, expected: T, actual: T): Unit = {
    org.junit.Assert.assertEquals(s"$what mismatch", expected, actual)
  }

  private def pairModules[T <: Attributed, U](expected: Seq[T], actual: Seq[U])(implicit nameOfT: HasName[T], nameOfU: HasName[U]) =
    pairByName(expected, actual)

  private def pairByName[T, U](expected: Seq[T], actual: Seq[U])(implicit nameOfT: HasName[T], nameOfU: HasName[U]): Seq[(T, U)] =
    expected.flatMap(e => actual.find(a => nameOfU(a) == nameOfT(e)).map((e, _)))

  protected def assertNoNotificationsShown(myProject: Project): Unit = {
    myProject.getUserData(ShownNotificationsKey) match {
      case null =>
      case notifications =>
        fail(
          s"""Expected no notifications, but following notifications were shown:
             |${notifications.map(notificationMessage).mkString("\n")}""".stripMargin
        )
    }
  }

  private def notificationMessage(shownNotification: ShownNotification) = {
    val data = shownNotification.data
    s"""Notification was shown during ${shownNotification.id} module creation.
       |Category: ${data.getNotificationCategory}
       |Title: ${data.getTitle}
       |Message: ${data.getMessage}
       |NotificationSource: ${data.getNotificationSource}
       |""".stripMargin
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

  import scala.language.implicitConversions

  private implicit def convertMatchType(mt: DslUtils.MatchType): AttributeMatchType = mt match {
    case MatchType.Exact   => AttributeMatchType.Exact
    case MatchType.Inexact => AttributeMatchType.Inexact
  }

  sealed trait AttributeMatchType {
    def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit
  }
  object AttributeMatchType {
    object Exact extends AttributeMatchType {
      override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit = {
        if (expected != actual) {
          val expectedConcatenated = expected.mkString("\n")
          val actualConcatenated = actual.mkString("\n")
          throw new ComparisonFailure(s"$what mismatch", expectedConcatenated, actualConcatenated)
        }
      }
    }

    object Inexact extends AttributeMatchType {
      override def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T]): Unit =
        expected.foreach { it =>
          if (!actual.contains(it)) {
            val message =
              s"""$what mismatch (should contain at least '$it')
                 |Expected [ ${expected.toList} ]
                 |Actual   [ ${actual.toList} ]""".stripMargin
            val prefix = "!!! INEXACT COMPARISON !!!\n"
            throw new ComparisonFailure(message, prefix + expected.mkString("\n"), prefix + actual.mkString("\n"))
          }
        }
    }
  }

  /**
   * @param strictCheckForBuildModules if `false` then if expected project structure doesn't contain `-build` modules it will not be considered as a test failure<br>
   *                                   if `true` then all the modules will be checked
   * @note there is also [[org.jetbrains.sbt.DslUtils.MatchType]]
   */
  case class ProjectComparisonOptions(strictCheckForBuildModules: Boolean)

  object ProjectComparisonOptions {
    object Implicit {
      implicit def default: ProjectComparisonOptions = ProjectComparisonOptions(strictCheckForBuildModules = false)
    }
  }
}

trait InexactMatch {
  self: ProjectStructureMatcher =>

  override def defaultAssertMatch: AttributeMatchType = AttributeMatchType.Inexact
}

trait ExactMatch {
  self: ProjectStructureMatcher =>

  override def defaultAssertMatch: AttributeMatchType = AttributeMatchType.Exact
}

