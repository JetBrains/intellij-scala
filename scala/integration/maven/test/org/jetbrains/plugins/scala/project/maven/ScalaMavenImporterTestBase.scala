package org.jetbrains.plugins.scala.project.maven

import com.intellij.maven.testFramework.fixtures.{MavenFixturesKt, MavenImportingTestFixture, MavenTestFixtureImportKt}
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.CoroutinesKt
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureAssertionsFixture
import org.jetbrains.sbt.project.ProjectStructureDsl.project
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.TestInfo

import java.nio.file.{Files, Path}
import java.util.Collections
import kotlin.coroutines.Continuation

/**
 * Shared machinery for Maven importing tests. Deliberately contains no `@Test` methods, so test suites can extend
 * it without inheriting each other's tests.
 *
 * @param projectJdkVersion the JDK version to set up as the project JDK around each test;
 *                          None means use the default JDK set up by the Maven test fixture ("Maven Test JDK")
 */
@TestApplication
abstract class ScalaMavenImporterTestBase(projectJdkVersion: Option[LanguageLevel]):

  /**
   * The fixture must be defined as a class member in order to be initialized (per test method, before the test)
   * and torn down (after the test) by the `TestFixtureExtension` installed via `@TestApplication`.
   *
   * Note: the fixture project is always named "project" (it is created in a fresh temp directory), not after the
   * test method. Expected project names in assertions use [[getProject]]`.getName`.
   */
  private val mavenFixture: TestFixture[MavenImportingTestFixture] =
    MavenFixturesKt.mavenImportingFixture(
      "bundled",                          // mavenVersion
      MavenConstants.MODEL_VERSION_4_0_0, // modelVersion
      true,                               // skipPluginResolution
      null                                // initialPom: each test imports its own pom from testdata
    )

  protected def getProject: Project = mavenFixture.get().getProject

  /**
   * Sets up the project JDK for `projectJdkVersion` around `test` and removes it afterwards.
   * SmartJDKLoader registers the JDK in the application-level table without a disposable,
   * so it must be removed manually.
   */
  protected def withProjectJdk(test: => Unit): Unit =
    projectJdkVersion match
      case Some(jdkVersion) =>
        val jdk = WriteAction.computeAndWait: () =>
          val jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
          ProjectRootManager.getInstance(getProject).setProjectSdk(jdk)
          jdk

        try test
        finally WriteAction.runAndWait(() => JavaAwareProjectJdkTableImpl.getInstanceEx.removeJdk(jdk))
      case None =>
        test

  private def getTestProjectDir(using testInfo: TestInfo): Path =
    //Replacement for the JUnit 3 getTestName(true): no test method name here starts with "test",
    //so the old value was the method name verbatim. It maps 1:1 to the testdata directory names.
    val testMethodName = testInfo.getTestMethod.get().getName
    val testDataPath = Path.of(TestUtils.getTestDataPath)
      .getParent.getParent
      .resolve("integration").resolve("maven").resolve("testdata").resolve("maven").resolve("projects")
      .resolve(testMethodName)
    assert(Files.exists(testDataPath), s"testdata directory not found: $testDataPath")
    testDataPath

  protected def getTestProjectDirVFile(using TestInfo): VirtualFile =
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(getTestProjectDir)

  /** Bridges a single Kotlin `suspend` function call into blocking code. */
  private def runBlockingUnit(body: Continuation[? >: kotlin.Unit] => AnyRef): Unit =
    CoroutinesKt.runBlockingMaybeCancellable[kotlin.Unit]((_, cont) => body(cont))

  protected def runImportingTest(expected: project)(using TestInfo): Unit =
    val pomFile = getTestProjectDir.resolve("pom.xml")

    val pomVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pomFile)
    assertNotNull(pomVFile, "can't find 'pom.xml' file")

    val fixture = mavenFixture.get()

    //1:1 port of the deprecated MavenImportingTestCase.importProjects: doImportProjectsAsync sets the original
    //files and explicit profiles, runs an incremental sync and asserts that there are no reading errors...
    runBlockingUnit: cont =>
      MavenTestFixtureImportKt.doImportProjectsAsync(
        fixture,
        Collections.singletonList(pomVFile),
        true,                // failOnReadingError
        Array.empty[String], // profiles
        cont
      )

    //...unlike the old importProjects, it does not wait for indexes and project configuration, so do it here
    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
    runBlockingUnit(MavenTestFixtureImportKt.awaitConfiguration(fixture, _))

    ProjectStructureAssertionsFixture(getProject).assertProjectsEqual(expected)
  end runImportingTest
end ScalaMavenImporterTestBase
