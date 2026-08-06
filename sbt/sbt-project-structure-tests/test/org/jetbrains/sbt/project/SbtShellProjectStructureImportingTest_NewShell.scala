package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.Assert

class SbtShellProjectStructureImportingTest_NewShell extends SbtShellProjectStructureImportingTestBase {
  override protected def useNewShell: Boolean = true

  def testShellCustomPrompt(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")

    runSimpleTest("root", "2.13", scalaLibraries)

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("root.test", "root.main"),
      getMyProject.modulesWithScala.map(_.getName),
    )
  }
}
