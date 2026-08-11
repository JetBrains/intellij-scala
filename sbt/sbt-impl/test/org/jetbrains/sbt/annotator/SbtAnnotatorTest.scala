package org.jetbrains.sbt
package annotator

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.{FixturesKt, TestFixture}
import com.intellij.testFramework.{IndexingTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message}
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaSDKLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.base.{ScalaSdkOwner, SourceRootTestUtil}
import org.jetbrains.plugins.scala.extensions.{PathExt, inReadAction}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagerForSbt
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.{Assumptions, Test}

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

/**
 * Checks the messages produced by [[SbtAnnotator]] on `testdata/annotator/Sbt/SbtAnnotator.sbt`.
 *
 * @param sbtVersion          the sbt version to resolve as a library of the `-build` module
 * @param defaultScalaVersion the Scala version to set up the Scala SDK for, unless one is globally configured
 *                            via `SCALA_SDK_TEST_VERSION`
 * @param supportedIn         which Scala versions this test supports; only consulted when a Scala version is
 *                            globally configured, to skip the test for unsupported ones
 */
@TestApplication
abstract class SbtAnnotatorTestBase(
  sbtVersion: SbtVersion,
  defaultScalaVersion: ScalaVersion,
  supportedIn: ScalaVersion => Boolean
):
  private val MainModuleName = "testModule"

  /**
   * The fixtures must be defined as class members in order to be initialized (per test method, before the test)
   * and torn down (after the test) by the `TestFixtureExtension` installed via `@TestApplication`.
   *
   * The project must be opened (`openAfterCreation = true`) so that the project startup activities run.
   * Without them the Scala synthetic classes are never registered and standard types like `scala.Nothing`
   * (the result type of `scala.Predef.???`) do not resolve.
   */
  private val projectFixture: TestFixture[Project] =
    FixturesKt.projectFixture(FixturesKt.tempPathFixture(), OpenProjectTask.build(), true)
  private val mainModuleFixture: TestFixture[Module] =
    FixturesKt.moduleFixture(projectFixture, MainModuleName, null)
  //example "testModule-build"; in tests, SbtBuildModuleSupport finds the build module by this naming convention
  private val buildModuleFixture: TestFixture[Module] =
    FixturesKt.moduleFixture(projectFixture, MainModuleName + Sbt.BuildModuleSuffix, SbtModuleType.instance.getId)

  protected def getProject: Project = projectFixture.get()
  protected def getModule: Module = mainModuleFixture.get()

  private def testdataPath: String = s"${TestUtils.getTestDataPath}/annotator/Sbt"

  /**
   * Sets up around `test` everything the JUnit 3 predecessor did in `setUp`: a JDK, the Scala SDK and the whole
   * transitive `org.scala-sbt:sbt` artifact as libraries of the `-build` module, the testdata source root and the
   * linked sbt project settings.
   *
   * It runs on the JUnit 5 worker thread, which keeps the blocking Ivy resolution of the sbt artifact off the EDT
   * (the JUnit 3 predecessor resolved it in `setUp` on the EDT, slowing down everything scheduled behind it).
   */
  protected final def withSbtProjectSetUp(test: => Unit): Unit =
    ScalaSdkOwner.globalConfiguredScalaVersion.foreach: configuredVersion =>
      Assumptions.assumeTrue(supportedIn(configuredVersion), s"Not supported in Scala version $configuredVersion")
    val scalaVersion = ScalaSdkOwner.globalConfiguredScalaVersion.getOrElse(defaultScalaVersion)

    val buildModule = buildModuleFixture.get()

    // A real JDK, like in the JUnit 3 predecessor. SmartJDKLoader registers it in the application-level JDK table
    // without a disposable, so it must be removed manually in the end.
    val jdk = WriteAction.computeAndWait: () =>
      val jdk = SmartJDKLoader.getOrCreateJDK()
      ProjectRootManager.getInstance(getProject).setProjectSdk(jdk)
      ModuleRootModificationUtil.setModuleSdk(getModule, jdk)
      ModuleRootModificationUtil.setModuleSdk(buildModule, jdk)
      jdk

    try
      ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true).init(using buildModule, scalaVersion)
      IvyManagedLoader(
        TestDependencyManagerForSbt(sbtVersion),
        ("org.scala-sbt" % "sbt" % sbtVersion.minor).transitive()
      ).init(using buildModule, scalaVersion)

      SourceRootTestUtil.addSourceRoot(getModule, Path.of(testdataPath))
      setUpProjectSettings()
      IndexingTestUtil.waitUntilIndexesAreReady(getProject)

      test
    finally
      WriteAction.runAndWait(() => JavaAwareProjectJdkTableImpl.getInstanceEx.removeJdk(jdk))
  end withSbtProjectSetUp

  protected final def runTest(expectedMessages: Seq[Message]): Unit =
    withSbtProjectSetUp:
      val actualMessages = annotate().asJava
      UsefulTestCase.assertSameElements(actualMessages, expectedMessages*)

  /** Must be called under a read action. */
  protected final def loadTestFile(): SbtFileImpl =
    val filePath = Path.of(testdataPath, "SbtAnnotator.sbt").toCanonicalPath
    val virtualFile = LocalFileSystem.getInstance.findFileByNioFile(filePath)
    assertNotNull(virtualFile, filePath.toString)
    val sbtFile = PsiManager.getInstance(getProject).findFile(virtualFile).asInstanceOf[SbtFileImpl]
    sbtFile.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    sbtFile

  private def annotate(): Seq[Message] = inReadAction:
    val file = loadTestFile()
    val mock = AnnotatorHolderMock(file)
    val annotator = SbtAnnotator()
    annotator.annotate(file)(mock)
    mock.annotations

  private def setUpProjectSettings(): Unit =
    val projectSettings = SbtProjectSettings.default
    val projectBasePath = getProject.getBasePath
    assertNotNull(projectBasePath)
    projectSettings.setExternalProjectPath(projectBasePath)
    SbtSettings.getInstance(getProject).linkProject(projectSettings)
    setSbtVersion(sbtVersion)
    // The JUnit 3 predecessor also called the deprecated Module.setOption("external.root.project.path", ...) and
    // SbtProjectSettings.setModules(...). Neither had any effect: setOption writes ModuleCustomImlDataEntity while
    // the sbt version lookup reads ExternalSystemModuleOptionsEntity (and it was set on the main module while the
    // annotator queries the build module). SbtAnnotator therefore falls back to SbtVersion.Latest.Sbt_1, as before.

  private def setSbtVersion(sbtVersion: SbtVersion): Unit =
    val projectBasePath = getProject.getBasePath
    assertNotNull(projectBasePath)
    val projectSettings = SbtSettings.getInstance(getProject).getLinkedProjectSettings(projectBasePath)
    assertNotNull(projectSettings)
    projectSettings.setSbtVersion(sbtVersion.minor)
end SbtAnnotatorTestBase

class SbtAnnotatorTest_1 extends SbtAnnotatorTestBase(
  SbtVersion.Latest.Sbt_1,
  ScalaVersion.Latest.Scala_2_12,
  _ >= ScalaVersion.Latest.Scala_2_12
):
  @Test
  def test(): Unit = runTest(Expectations.sbt_1_0)

class SbtAnnotatorTest_2 extends SbtAnnotatorTestBase(
  SbtVersion.Latest.Sbt_2,
  ScalaVersion.Latest.Scala_3,
  _.isScala3
):
  @Test
  def test(): Unit = runTest(Expectations.sbt_2)

/**
  * Expected error messages for specific sbt versions. Newer versions usually allow more syntactic constructs in the sbt files
  */
object Expectations:
  import Message.*

  val sbtAll: Seq[Error] = Seq(
    Error("object Bar", SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )

  val sbt_1_0: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "String")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136"))
  )

  // TODO: we need to review SBT 2.0 new rules and adopt SbtAnnotator.scala along with the expected data
  val sbt_2: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "\"some string\"")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136"))
  )
end Expectations
