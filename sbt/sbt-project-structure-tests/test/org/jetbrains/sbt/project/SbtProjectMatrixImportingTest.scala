package org.jetbrains.sbt.project

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.SbtProjectMatrixTestUtils.Fixture
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.nio.file.Files

@Category(Array(classOf[SlowTests2]))
abstract class SbtProjectMatrixImportingTest(fixture: Fixture)
  extends SbtProjectStructureImportingTestBase
    with ProjectStructureMatcher
    with ExactMatch {

  override protected def getTestDataProjectPath: String = fixture.testDataPath

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  override protected def useNewLogicForSourceFolderComparison: Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    CompileServerTestUtil.registerLongRunningThreads()
    injectVariable(getTestProjectPath.resolve("project/build.properties"), "$LATEST_SBT_2$", SbtVersion.Latest.Sbt_2.minor)
  }

  def testImportAndBuild(): Unit = {
    val notifications = CollectingNotificationsListener.subscribeOnWarningsAndErrors(getMyProject)
    importProject(false)
    val compareContext = defaultCompareContext.withOptions(_.copy(strictCheckForBuildModules = true))
    assertProjectsEqual(SbtProjectMatrixTestUtils.expectedProject(fixture), getMyProject)(using compareContext)
    assertNoNotificationsShown(getMyProject, notifications.getNotifications)

    buildCrossProjectAndAssertNoWarningsOrErrors()

    // A successful no-op build must not hide missing roots: check shared, generated, and platform outputs in every row.
    for {
      matrixName <- fixture.matrixNames
      platform <- fixture.platforms
      version <- fixture.scalaVersions
      (scope, outputDirectory) <- Seq("main" -> "classes", "test" -> "test-classes")
      className <- Seq(s"Shared${scope.capitalize}", s"Generated${scope.capitalize}", s"Platform${scope.capitalize}")
    } {
      val relativePath =
        s"target/out/${platform.outputDirectory}/scala-$version/$matrixName/$outputDirectory/example/$matrixName/$className.class"
      assertTrue(s"Missing compiled output: $relativePath", Files.isRegularFile(getTestProjectPath.resolve(relativePath)))
    }
  }
}

class SbtProjectMatrixImportingTest_JvmAndJs
  extends SbtProjectMatrixImportingTest(SbtProjectMatrixTestUtils.JvmAndJs)

class SbtProjectMatrixImportingTest_JsAndNative
  extends SbtProjectMatrixImportingTest(SbtProjectMatrixTestUtils.JsAndNative)

class SbtProjectMatrixImportingTest_JvmAndJsAndNative
  extends SbtProjectMatrixImportingTest(SbtProjectMatrixTestUtils.JvmAndJsAndNative)
