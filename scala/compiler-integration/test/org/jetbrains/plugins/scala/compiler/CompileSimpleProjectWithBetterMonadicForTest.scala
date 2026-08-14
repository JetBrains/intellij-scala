package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, TestUtils}
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.{RequiresJdk, SbtExternalSystemImportingTestLike}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Reproduces [[https://youtrack.jetbrains.com/issue/SCL-25761]].
 *
 * In sbt 2, the paths in scalac options can contain placeholders (e.g. `-Xplugin:${CSR_CACHE}/.../better-monadic-for.jar`).
 * These placeholders are resolved during the sbt structure extraction ([[https://github.com/JetBrains/sbt-structure/commit/84b72ea9f28d4253caff2254af4953b4b7ce1d0a]]).
 * This test verifies that compiler plugin paths containing placeholders are resolved correctly
 * and the project compiles successfully in both sbt 1.x and sbt 2.x.
 */
@RunWith(classOf[JUnit4])
abstract class CompileSimpleProjectWithBetterMonadicForTestBase extends SbtExternalSystemImportingTestLike {

  protected def sbtVersion: SbtVersion

  override protected lazy val getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/compilation/projects/${getTestName(true)}"

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    injectVariable(getTestProjectPath / "project" / "build.properties", "$SBT_VERSION$", sbtVersion.minor)
  }

  override def tearDown(): Unit =
    try {
      val table = ProjectJdkTable.getInstance(getMyProject)
      inWriteAction(table.getAllJdks.foreach(table.removeJdk))
    } finally {
      super.tearDown()
    }

  @Test
  def testBetterMonadicFor(): Unit = {
    importProject(false)

    val revertible = CompilerTestUtil.withEnabledCompileServer(true)
    revertible.run {
      val compiler = new CompilerTester(getMyProject, java.util.List.of(getMyTestFixture.getModule), null, false)
      try {
        compiler.rebuild().assertNoProblems()
      } finally {
        compiler.tearDown()
        CompileServerLauncher.stopServerAndWait()
      }
    }
  }
}

@Category(Array(classOf[SlowTests]))
class CompileSimpleProjectWithBetterMonadicForTest_Sbt_1 extends CompileSimpleProjectWithBetterMonadicForTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1
}

@Category(Array(classOf[SlowTests]))
@RequiresJdk(LanguageLevel.JDK_17)
class CompileSimpleProjectWithBetterMonadicForTest_Sbt_2 extends CompileSimpleProjectWithBetterMonadicForTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_2
}
