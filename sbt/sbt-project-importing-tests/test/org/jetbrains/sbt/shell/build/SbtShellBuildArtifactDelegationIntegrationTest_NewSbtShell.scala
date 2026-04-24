package org.jetbrains.sbt.shell.build

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.RequiresJdk
import org.junit.experimental.categories.Category

// NOTE: for the new sbt shell we run only 1 health check test
// The core tested part is not the new sbt shell itself but integration of the "build delegating to sbt" and "build artifact"
// The new sbt shell building should be tested more exhaustively in the tests for core building
// TODO: once the new sbt shell is enabled by default, move the legacy tests to this class
@Category(Array(classOf[SlowTests2]))
@RequiresJdk(LanguageLevel.JDK_17)
class SbtShellBuildArtifactDelegationIntegrationTest_NewSbtShell
  extends SbtShellBuildArtifactDelegationIntegrationTestBase {

  override protected def useNewSbtShell: Boolean = true

  def testBuildArtifact_sbt_1_11(): Unit = {
    runBuildArtifactDelegationTest(sbtVersion = SbtVersion.Latest.Sbt_1_11)
  }
}
