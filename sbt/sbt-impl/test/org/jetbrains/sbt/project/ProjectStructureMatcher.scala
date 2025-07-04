package org.jetbrains.sbt
package project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.notification.Notification
import com.intellij.openapi.externalSystem.service.project.manage.{SourceFolderManager, SourceFolderManagerImpl, SourceFolderModelState}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{CompilerModuleExtension, ContentEntry, LanguageLevelModuleExtensionImpl, LibraryOrderEntry, ModuleOrderEntry, ModuleRootManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.{CommonProcessors, PathUtil}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{PathExt, ThrowableExt}
import org.jetbrains.plugins.scala.project.external.{SdkReference, SdkUtils, ShownNotification, ShownNotificationsKey}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ProjectExt, ScalaLibraryProperties}
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils
import org.jetbrains.sbt.DslUtils.MatchType
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.AttributeMatchType
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext.AssertionFailStrategy
import org.jetbrains.sbt.project.utils.{MacroSubstitutor, ProjectStructureComparisonContext, ScalaCliStructureHelper}
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue, fail}
import org.junit.{Assert, ComparisonFailure}

import java.nio.file.Path
import scala.jdk.CollectionConverters._

trait ProjectStructureMatcher {

  import ProjectStructureMatcher._

  protected def defaultAssertMatch: AttributeMatchType

  final def assertMatch[T](what: String, expected: Seq[T], actual: Seq[T])
                          (mt: Option[DslUtils.MatchType])
                          (implicit compareContext: ProjectStructureComparisonContext): Unit = compareContext.assertionFailStrategy.run {
    val actualAdopted = if (actual.headOption.exists(_.isInstanceOf[String]))
      compareContext.macroSubstitutor
        .replaceValuesWithMacro(actual.map(_.asInstanceOf[String]), expected.map(_.asInstanceOf[String]))
        .map(_.asInstanceOf[T])
    else
      actual
    val matcher = mt.map(convertMatchType).getOrElse(defaultAssertMatch)
    matcher.assertMatch(what, expected, actualAdopted)
  }

  def assertMatchWithIgnoredOrder[T : Ordering](what: String, expected: Seq[T], actual: Seq[T])
                                               (mt: Option[DslUtils.MatchType])
                                               (implicit compareContext: ProjectStructureComparisonContext): Unit =
    assertMatch(what, expected.sorted, actual.sorted)(mt)

  def assertProjectsEqual(
    expected: project,
    actual: Project,
    singleContentRootModules: Boolean = true
  )(implicit compareContext: ProjectStructureComparisonContext): Unit = {
    assertEquals("Project name", expected.name, actual.getName)
    expected.foreach0(sdk)(assertProjectSdkEqual(actual))
    expected.foreach(libraries)(assertProjectLibrariesEqual(actual))
    expected.foreach0(javaLanguageLevel)(assertProjectJavaLanguageLevel(actual))
    expected.foreach0(javaTargetBytecodeLevel)(assertProjectJavaTargetBytecodeLevel(actual))
    expected.foreach0(javacOptions)(assertProjectJavacOptions(actual))

    expected.foreach(modules)(assertProjectModulesEqual(actual, singleContentRootModules)(_))
    expected.foreach(packagePrefix)(assertPackagePrefixEqual(actual, singleContentRootModules)(_))

    compareContext.assertionFailStrategy match {
      case collect: AssertionFailStrategy.CollectErrors => 
        val assertionErrors = collect.getAssertionErrors
        createSeparateFailedNodesInTestTreeView(assertionErrors)

        // throw the first error as a primary exception to fail the current test
        assertionErrors.headOption.foreach(throw _)
      case _ =>
    }
  }

  // Uses special service messages in order IntelliJ creates separate test nodes for each assertion error.
  // It makes it more convenient to explore multiple failures when working with big projects expected structure
  private def createSeparateFailedNodesInTestTreeView(assertionErrors: Seq[AssertionError]): Unit = {
    // don't print all errors to avoid spamming too much output in the failed tests
    val maxErrorsToPrint = 20

    assertionErrors.take(maxErrorsToPrint).zipWithIndex.foreach { case (error, idx) =>
      val testName = s"error ${idx + 1} / ${assertionErrors.size}"
      val messageEscaped = TeamcityUtils.escapeTeamcityValue(error.stackTraceText)
      println(s"##teamcity[testFailed name='$testName' message='$messageEscaped']")
    }
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

  private def assertProjectSdkEqual(project: Project)(expectedSdkRef: SdkReference)
                                   (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val expectedSdk = SdkUtils.findProjectSdk(expectedSdkRef).getOrElse {
      fail(s"Sdk $expectedSdkRef nof found").asInstanceOf[Nothing]
    }
    val actualSdk = roots.ProjectRootManager.getInstance(project).getProjectSdk
    assertEquals("Project SDK", expectedSdk, actualSdk)
  }

  private def assertProjectModulesEqual(project: Project, singleContentRootModules: Boolean)
                                       (expectedModules: Seq[module])(mt: Option[MatchType])
                                       (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val actualModulesAll = ModuleManager.getInstance(project).getModules.toSeq
    val actualModules =
      if (compareContext.options.strictCheckForBuildModules || expectedModules.exists(_.isBuildModule)) actualModulesAll
      else actualModulesAll.filterNot(_.isBuildModule)
    assertNamesEqualIgnoreOrder("Project module", expectedModules, actualModules)(mt)
    val pairs = pairModules(expectedModules, actualModules)
    pairs.foreach { case(exp, actual) => assertModulesEqual(exp, actual, singleContentRootModules) }
  }

  private def assertModulesEqual(
    expected: module,
    actual: Module,
    singleContentRootModules: Boolean
  )(implicit compareContext: ProjectStructureComparisonContext): Unit = {
    import ProjectStructureDsl._

    expected.foreach(contentRoots)(assertModuleContentRootsEqual(actual))
    expected.foreach(sources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.SOURCE, "Sources", singleContentRootModules))
    expected.foreach(testSources)(assertModuleContentFoldersEqual(actual, JavaSourceRootType.TEST_SOURCE, "Test sources", singleContentRootModules))
    expected.foreach(resources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.RESOURCE, "Resources", singleContentRootModules))
    expected.foreach(testResources)(assertModuleContentFoldersEqual(actual, JavaResourceRootType.TEST_RESOURCE, "Test resources", singleContentRootModules))
    expected.foreach(excluded)(assertModuleExcludedFoldersEqual(actual, singleContentRootModules))
    expected.foreach(moduleDependencies)(assertModuleDependenciesEqual(actual))
    expected.foreach(libraryDependencies)(assertLibraryDependenciesEqual(actual))
    expected.foreach(libraries)(assertModuleLibrariesEqual(actual))
    expected.foreach0(moduleFileDirectoryPath)(asserModuleFileDirectoryPathEqual(actual))

    expected.foreach0(javaLanguageLevel)(assertModuleJavaLanguageLevel(actual))
    expected.foreach0(javaTargetBytecodeLevel)(assertModuleJavaTargetBytecodeLevel(actual))
    expected.foreach(javacOptions)(assertModuleJavacOptions(actual))
    expected.foreach0(compileOrder)(assertModuleCompileOrder(actual))
    expected.foreach0(compileOutputPath)(assertModuleCompileOutputPath(actual))
    expected.foreach0(compileTestOutputPath)(assertModuleCompileOutputPath(actual, test = true))

    lazy val sbtModuleData = SbtUtil.getSbtModuleData(actual).getOrElse {
      org.junit.Assert.fail(s"Can't get module data for module: $actual (${actual.getModuleFilePath})").asInstanceOf[Nothing]
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

  private def assertProjectJavaLanguageLevel(project: Project)(expected: LanguageLevel)
                                            (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val settings = roots.LanguageLevelProjectExtension.getInstance(project)
    val actual = settings.getLanguageLevel
    assertEquals("Project java language level", expected, actual)
  }

  private def assertProjectJavaTargetBytecodeLevel(project: Project)(expected: String)
                                                  (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(project)
    val actual = compilerSettings.getProjectBytecodeTarget
    assertEquals("Project target bytecode level (for Java sources)", expected, actual)
  }

  protected def assertModuleJavacOptions(module: Module)(expectedOptions: Seq[String])(mt: Option[MatchType]): Unit = {
    val settings = CompilerConfiguration.getInstance(module.getProject)
    val actual = settings.getAdditionalOptions(module).asScala
    Assert.assertEquals(s"Module javacOptions (${module.getName})", expectedOptions, actual)
  }

  private def assertModuleCompileOrder(module: Module)(expected: CompileOrder)
                                      (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val settings = ScalaCompilerSettings.forModule(module)
    assertEquals("Compile order", expected, settings.compileOrder)
  }

  private def assertModuleCompileOutputPath(module: Module, test: Boolean = false)
                                           (expected: String)
                                           (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val extension = CompilerModuleExtension.getInstance(module)
    val actualPath = if (test) extension.getCompilerOutputUrlForTests else extension.getCompilerOutputUrl
    val actualPathString = if (actualPath == null)
      null
    else if (compareContext.macroSubstitutor.containsSomeMacro(expected))
      actualPath.stripPrefix("file://")
    else {
      val contentRoots = getContentRoots(module)
      assertTrue(s"assertModuleCompileOutputPath expects a single content root or ${MacroSubstitutor.Keys.ProjectRoot} macro key in the expected path", contentRoots.size == 1)
      val contentRoot = contentRoots.head

      mapContentFolderToUrl(actualPath, contentRoot.getUrl)
    }
    assertEquals("Compilation output path", expected, actualPathString)
  }

  private def assertProjectJavacOptions(project: Project)(expectedOptions: Seq[String])
                                       (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val settings = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    val actual = settings.ADDITIONAL_OPTIONS_STRING
    assertEquals("Project javacOptions", expectedOptions.mkString(" "), actual)
  }

  private def assertModuleContentRootsEqual(module: Module)(expected: Seq[String])(mt: Option[MatchType])
                                           (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val expectedRoots = expected
    val actualRoots = roots.ModuleRootManager.getInstance(module).getContentEntries.map(_.getUrl.stripPrefix("file://")).toSeq
    assertMatchWithIgnoredOrder(s"Content root of module `${module.getName}`", expectedRoots, actualRoots)(mt)
  }

  /**
   * When set to true, allows to check all source roots, even if they don't exist on disk.
   * This only supports relative paths from the project root or absolute paths
   * TODO: make this true by default and patch all our tests
   */
  protected def useNewLogicForSourceFolderComparison = false

  private def assertModuleContentFoldersEqual(
    module: Module,
    folderType: JpsModuleSourceRootType[_],
    folderTypeDisplayName: String,
    //TODO: drop this parameter and patch test data, it seems it's not needed since we introduced code inside getActualSourceRoots
    singleContentRootModules: Boolean
  )(expected: Seq[String])(mt: Option[MatchType])(
    implicit compareContext: ProjectStructureComparisonContext
  ): Unit = {
    if (expected.isEmpty || useNewLogicForSourceFolderComparison) {
      val sourceFolders = getSourceRootUrls(module, folderType).map(_.stripPrefix("file://"))
      assertMatchWithIgnoredOrder(s"$folderTypeDisplayName of module '${module.getName}'", expected, sourceFolders)(mt)
    }
    else {
      val contentRoots = getContentRoots(module)
      if (singleContentRootModules) {
        assertSingleContentRoot(contentRoots, module.getName)
      }
      val contentRootToSourceFolders = contentRoots.map { contentRoot =>
        contentRoot -> contentRoot.getSourceFolders(folderType).asScala.toSeq
      }.toMap
      assertContentRootFoldersEqual(folderTypeDisplayName, module, contentRootToSourceFolders, expected)(mt)
    }
  }

  //noinspection ApiStatus,UnstableApiUsage
  private def getSourceRootUrls(module: Module, sourceRootType: JpsModuleSourceRootType[_]): Seq[String] = {
    val sourceRootsFromContentRoots = getSourceRootUrlsFromContentRotos(module, sourceRootType)

    if (useNewLogicForSourceFolderComparison) {
      val sourceRootsFromManager = getSourceRootUrlsFromSourceFolderManager(module, sourceRootType)
      // NOTE: we need to merge both results because both are not perfect:
      // source roots from manager don't contain source roots that are equal to content roots
      // (for example, dir in `./project` is both content and source root for a `-build` module
      (sourceRootsFromManager ++ sourceRootsFromContentRoots).distinct
    }
    else {
      sourceRootsFromContentRoots
    }
  }

  private def getSourceRootUrlsFromContentRotos(module: Module, sourceRootType: JpsModuleSourceRootType[_]): Seq[String] = {
    val contentRoots = getContentRoots(module)
    contentRoots.flatMap(_.getSourceFolders(sourceRootType).asScala.toSeq).map(_.getUrl)
  }

  private def getSourceRootUrlsFromSourceFolderManager(module: Module, sourceRootType: JpsModuleSourceRootType[_]): Seq[String] = {
    val sourceFolderManager = SourceFolderManager.getInstance(module.getProject).asInstanceOf[SourceFolderManagerImpl]
    val sourceFolderModels: Seq[SourceFolderModelState] =
      sourceFolderManager.getState.getSourceFolders.asScala.toSeq
    sourceFolderModels
      .filter { model =>
        model.getModuleName == module.getName &&
          model.getType == SourceTypeToString(sourceRootType)
      }
      .map(_.getUrl)
  }

  //reverse of SourceFolderManagerImpl.kt/dictionary mapping
  private val SourceTypeToString: Map[JpsModuleSourceRootType[_], String] = Map(
     JavaSourceRootType.SOURCE -> "SOURCE",
     JavaSourceRootType.TEST_SOURCE -> "TEST_SOURCE",
     JavaResourceRootType.RESOURCE -> "RESOURCE",
     JavaResourceRootType.TEST_RESOURCE -> "TEST_RESOURCE",
  )

  private def assertSingleContentRoot(contentRoots: Seq[ContentEntry], moduleName: String)
                                     (implicit compareContext: ProjectStructureComparisonContext): Unit =
    assertEquals(s"Expected single content root in module $moduleName, Got: $contentRoots", 1, contentRoots.length)

  private def assertModuleExcludedFoldersEqual(module: Module, singleContentRootModules: Boolean)
                                              (expected: Seq[String])(mt: Option[MatchType])
                                              (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val contentRoots = getContentRoots(module)
    // note: when singleContentRootModules is false, then there is a test with modules separated to main and test, and in such a case
    // checking for excluded folder files when expected Seq is empty is not correct.
    // For main and test modules expected will always be empty, but #getExcludeFolderFiles will return the module output.
    if (singleContentRootModules && expected.isEmpty) {
      val excludedFolderFiles = contentRoots.flatMap(_.getExcludeFolderFiles).map(_.getUrl)
      assertMatchWithIgnoredOrder(s"Excluded folders of module '${module.getName}'", Nil, excludedFolderFiles)(mt)
    } else {
      if (singleContentRootModules) assertSingleContentRoot(contentRoots, module.getName)
      val contentRootToExcludeFolders = contentRoots.map { contentRoot =>
        contentRoot -> contentRoot.getExcludeFolders.toSeq
      }.toMap
      assertContentRootFoldersEqual(s"Excluded folders", module, contentRootToExcludeFolders, expected)(mt)
    }
  }

  private def assertContentRootFoldersEqual(
    folderType: String,
    module: Module,
    contentRootToFolders: Map[roots.ContentEntry, Seq[roots.ContentFolder]],
    expected: Seq[String]
  )(
    mt: Option[MatchType]
  )(
    implicit compareContext: ProjectStructureComparisonContext
  ): Unit = {
    val actualFolders = contentRootToFolders.flatMap { case (contentRoot, contentFolders) =>
      if (expected.exists(compareContext.macroSubstitutor.containsSomeMacro))
        contentFolders.map(_.getUrl.stripPrefix("file://"))
      else
        contentFolders.map(mapContentFolderToUrl(_, contentRoot))
    }.toSeq
    assertMatchWithIgnoredOrder(s"$folderType of module '${module.getName}'", expected, actualFolders)(mt)
  }

  private def mapContentFolderToUrl(
    folder: roots.ContentFolder,
    contentRoot: ContentEntry
  ): String =
    mapContentFolderToUrl(folder.getUrl, contentRoot.getUrl)

  private def mapContentFolderToUrl(
    folderUrl: String,
    contentRootUrl: String
  ): String = {
    if (folderUrl.startsWith(contentRootUrl))
      folderUrl.substring(Math.min(folderUrl.length, contentRootUrl.length + 1))
    else
      folderUrl
  }

  private def getContentRoots(module: Module): Seq[ContentEntry] =
    roots.ModuleRootManager.getInstance(module).getContentEntries.toSeq

  private def assertPackagePrefixEqual(project: Project, singleContentRootModules: Boolean)
                                      (expectedPrefix: String)
                                      (mt: Option[MatchType])
                                      (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    project.modules.filterNot(_.isBuildModule).foreach { module =>
      val contentRoots = getContentRoots(module)
      if (singleContentRootModules) assertSingleContentRoot(contentRoots, module.getName)
      contentRoots.flatMap(_.getSourceFolders.toSeq).foreach { source =>
        assertEquals(s"package prefix for source folder $source of module `${module.getName}`", expectedPrefix, source.getPackagePrefix)
      }
    }
  }

  private def assertModuleDependenciesEqual(module: Module)(expected: Seq[dependency[module]])(mt: Option[MatchType])
                                           (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val actualModuleEntries = roots.OrderEnumerator.orderEntries(module).moduleEntries
    if (module.isTest && !module.isSharedSourceModule) {
      validateFirstDependencyInTestModule(module, actualModuleEntries)
    }
    assertNamesEqualIgnoreOrder(s"Module dependency of module `${module.getName}`", expected.map(_.reference), actualModuleEntries.map(_.getModule))(mt)
    val paired = pairModules(expected, actualModuleEntries)
    paired.foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  /**
   * Checks if the first non-shared sources module dependency in a test module dependencies is a dependency on the main module of the corresponding module.
   * This is a temporary approach until the full order of project dependencies is implemented.
   *
   * @see https://youtrack.jetbrains.com/issue/SCL-24063
   * @see https://youtrack.jetbrains.com/issue/SCL-24078/Maintain-the-actual-order-of-project-dependencies
   */
  private def validateFirstDependencyInTestModule(testModule: Module, orderEntries: Seq[ModuleOrderEntry]): Unit = {
    val firstNonSharedDependency = orderEntries.find { entry =>
      Option(entry.getModule).exists { module => !module.isSharedSourceModule}
    }
    val isValidDependency = firstNonSharedDependency.exists { entry =>
      val entryModule = entry.getModule
      if (entryModule != null && entryModule.isMain) {
        entryModule.getName.stripSuffix("main") == testModule.getName.stripSuffix("test")
      } else false
    }
    Assert.assertTrue(
      s"In the dependencies of the test module (${testModule.getName}), the first dependency is not the main module of the corresponding module.",
      isValidDependency
    )
  }

  private def assertLibraryDependenciesEqual(module: Module)(expected: Seq[dependency[library]])(mt: Option[MatchType])
                                            (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val actualLibraryEntries = roots.OrderEnumerator.orderEntries(module).libraryEntries
    val assertNamesMethod : (String, Seq[Named], Seq[Library]) => Option[MatchType] => Unit =
      if (compareContext.options.checkLibraryDependenciesOrder) assertNamesEqual
      else assertNamesEqualIgnoreOrder

    assertNamesMethod(s"Library dependency of module `${module.getName}`", expected.map(_.reference), actualLibraryEntries.map(_.getLibrary))(mt)

    assertUnmanagedLibraryIsAboveOtherLibrariesIfExists(actualLibraryEntries)
    pairByName(expected, actualLibraryEntries).foreach((assertDependencyScopeAndExportedFlagEqual _).tupled)
  }

  private def assertUnmanagedLibraryIsAboveOtherLibrariesIfExists(actual: Seq[LibraryOrderEntry]): Unit = {
    val librariesWithoutScalaSDK = actual.map(_.getLibrary).filterNot(_.isScalaSdk)
    val index = librariesWithoutScalaSDK.indexWhere(_.getName == s"sbt: ${Sbt.UnmanagedLibraryName}")
    assert(index == 0 || index == -1, "Library for unmanaged jars exists, but it is not the highest in the order")
  }

  private def assertDependencyScopeAndExportedFlagEqual(expected: dependency[_], actual: roots.ExportableOrderEntry)
                                                       (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    expected.foreach0(isExported)(it => assertEquals("Dependency isExported flag", it, actual.isExported))
    expected.foreach0(scope)(it => assertEquals("Dependency scope", it, actual.getScope))
  }

  private def assertProjectLibrariesEqual(project: Project)(expectedLibraries: Seq[library])(mt: Option[MatchType])
                                         (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val actualLibraries = project.libraries
    assertNamesEqualIgnoreOrder("Project library", expectedLibraries, actualLibraries)(mt)
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertLibraryContentsEqual(expected: library, actual: Library)
                                        (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    expected.foreach(libClasses)(assertLibraryFilesEqual(actual, roots.OrderRootType.CLASSES))
    expected.foreach(libSources)(assertLibraryFilesEqual(actual, roots.OrderRootType.SOURCES))
    expected.foreach(libJavadocs)(assertLibraryFilesEqual(actual, roots.JavadocOrderRootType.getInstance))
  }

  // TODO: support non-local library contents (if necessary)
  // This implementation works well only for local files; *.zip and other archives are not supported
  // @dancingrobot84
  private def assertLibraryFilesEqual(lib: Library, fileType: roots.OrderRootType)(expectedFiles: Seq[String])(mt: Option[MatchType])
                                     (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val expectedNormalized = expectedFiles.map(normalizePathSeparators)
    val actualNormalised = lib.getFiles(fileType).flatMap(f => Option(PathUtil.getLocalPath(f))).toSeq.map(normalizePathSeparators)
    assertMatch("Library file", expectedNormalized, actualNormalised)(mt)
  }

  private def normalizePathSeparators(path: String): String = path.replace("\\", "/")

  private def assertLibraryScalaSdk(expectedLibrary: library, actualLibrary0: Library)
                                   (implicit compareContext: ProjectStructureComparisonContext): Unit = {
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

        def testClasspath(name: String, expectedClasspathStr: Seq[String], actualClasspathFile: Seq[Path]): Unit = {
          val expectedClassPathNorm = expectedClasspathStr.map(normalizePathSeparators).sorted.mkString("\n")
          val actualClasspathNorm = actualClasspathFile.map(_.toCanonicalPath.toString).map(normalizePathSeparators).sorted.mkString("\n")
          assertEquals(name, expectedClassPathNorm, actualClasspathNorm)
        }

        expectedScalaSdk.classpath.foreach(testClasspath("Scala SDK classpath", _, sdkProperties.compilerClasspath))
        expectedScalaSdk.extraClasspath.foreach(testClasspath("Scala SDK extra classpath", _, sdkProperties.scaladocExtraClasspath))
    }
  }

  private def asserModuleFileDirectoryPathEqual(module: Module)(expected: String)
                                               (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val moduleName = module.getName
    val externalProjectRoot = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    assertNotNull(s"The external project root for $moduleName is null", externalProjectRoot)

    val defaultModuleFilesDirectory = SbtUtil.getDefaultModuleFilesDirectory(Path.of(externalProjectRoot).toFile)
    val expectedModuleFileDirectoryPath = Path.of(defaultModuleFilesDirectory, expected)
    assertEquals(s"The module file directory for $moduleName is incorrect", expectedModuleFileDirectoryPath, module.getModuleNioFile.getParent)
  }

  private def assertModuleLibrariesEqual(module: Module)(expectedLibraries: Seq[library])(mt: Option[MatchType])
                                        (implicit compareContext: ProjectStructureComparisonContext): Unit = {
    val actualLibraries = roots.OrderEnumerator.orderEntries(module).libraryEntries.filter(_.isModuleLevel).map(_.getLibrary)
    assertNamesEqualIgnoreOrder("Module library", expectedLibraries, actualLibraries)(mt)
    pairByName(expectedLibraries, actualLibraries).foreach { case (expected, actual) =>
      assertLibraryContentsEqual(expected, actual)
      assertLibraryScalaSdk(expected, actual)
    }
  }

  private def assertNamesEqualIgnoreOrder[T](what: String, expected: Seq[Named], actual: Seq[T])
                                            (mt: Option[MatchType])
                                            (implicit nameOf: HasName[T], compareContext: ProjectStructureComparisonContext): Unit = {
    val actualNames = actual.map(s => convertIfScalaCli(nameOf(s)))
    assertMatchWithIgnoredOrder(what, expected.map(_.name), actualNames)(mt)
  }

  private def assertNamesEqual[T](what: String, expected: Seq[Named], actual: Seq[T])
                                 (mt: Option[MatchType])
                                 (implicit nameOf: HasName[T], compareContext: ProjectStructureComparisonContext): Unit = {
    val actualNames = actual.map(s => convertIfScalaCli(nameOf(s)))
    assertMatch(what, expected.map(_.name), actualNames)(mt)
  }

  /**
   * In case of a Scala CLI project any occurrence of project name with a <code>_hash</code> suffix should be changed to just a project name.
   * It's not possible to predict a hash before tests, so it's necessary to compare the names of modules, libraries or dependencies.
   *
   * @see [[ScalaCliStructureHelper]]
   */
  private def convertIfScalaCli(name: String)(implicit compareContext: ProjectStructureComparisonContext): String =
    compareContext.options.scalaCliStructureHelper match {
      case Some(ScalaCliStructureHelper(pattern)) =>
        pattern.replaceAllIn(name, m => m.group(1))
      case _ => name
    }

  private def assertEquals(
    what: String,
    expected: String,
    actual: String
  )(implicit compareContext: ProjectStructureComparisonContext): Unit = compareContext.assertionFailStrategy.run {
    val actualAdapted = compareContext.macroSubstitutor.replaceValuesWithMacro(actual, expected)
    org.junit.Assert.assertEquals(s"$what mismatch", expected, actualAdapted)
  }

  private def assertEquals[T](
    what: String,
    expected: T,
    actual: T
  )(implicit compareContext: ProjectStructureComparisonContext): Unit = compareContext.assertionFailStrategy.run {
    val actualAdopted: T = actual match {
      case s: String =>
        compareContext.macroSubstitutor
          .replaceValuesWithMacro(s, expected.asInstanceOf[String])
          .asInstanceOf[T]
      case p: Path =>
        val pathNew = compareContext.macroSubstitutor.replaceValuesWithMacro(p.toString, expected.asInstanceOf[Path].toString)
        Path.of(pathNew).asInstanceOf[T]
      case _ =>
        actual
    }
    org.junit.Assert.assertEquals(s"$what mismatch", expected, actualAdopted)
  }

  private def pairModules[T <: Attributed, U](expected: Seq[T], actual: Seq[U])
                                             (implicit nameOfT: HasName[T], nameOfU: HasName[U], compareContext: ProjectStructureComparisonContext): Seq[(T, U)] =
    pairByName(expected, actual)

  private def pairByName[T, U](expected: Seq[T], actual: Seq[U])
                              (implicit nameOfT: HasName[T], nameOfU: HasName[U], compareContext: ProjectStructureComparisonContext): Seq[(T, U)] =
    expected.flatMap(e => actual.find(a => convertIfScalaCli(nameOfU(a)) == nameOfT(e)).map((e, _)))

  def assertNoNotificationsShown(myProject: Project, notifications: Seq[Notification] = Nil, mutedNotificationTitles: Seq[String] = Nil): Unit = {
    val nonMutedNotifications = notifications.filterNot(n => mutedNotificationTitles.contains(n.getTitle))
    if (nonMutedNotifications.nonEmpty) {
      val notificationsText = nonMutedNotifications.map(notificationMessage).mkString("\n")
      org.junit.Assert.fail(
        s"""Expected no notifications, but following notifications were shown:
           |$notificationsText""".stripMargin
      )
    }

    // check no custom notifications are shown
    // (this MIGHT be redundant as `notifications` parameter might already cover this, shouldn't it?)
    myProject.getUserData(ShownNotificationsKey) match {
      case null =>
      case notifications =>
        org.junit.Assert.fail(
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

  private def notificationMessage(shownNotification: Notification) = {
    s"""Notification was shown during ${shownNotification.id} module creation.
       |Group id: ${shownNotification.getGroupId}
       |Title: ${shownNotification.getTitle}
       |Subtitle: ${shownNotification.getSubtitle}
       |Content: ${shownNotification.getContent}
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
}

trait InexactMatch {
  self: ProjectStructureMatcher =>

  override def defaultAssertMatch: AttributeMatchType = AttributeMatchType.Inexact
}

trait ExactMatch {
  self: ProjectStructureMatcher =>

  override def defaultAssertMatch: AttributeMatchType = AttributeMatchType.Exact
}
