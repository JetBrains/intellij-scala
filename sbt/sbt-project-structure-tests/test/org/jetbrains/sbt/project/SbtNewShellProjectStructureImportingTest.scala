package org.jetbrains.sbt.project

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.Assert

class SbtNewShellProjectStructureImportingTest extends SbtShellProjectStructureImportingTest {

  override def setUp(): Unit = {
    super.setUp()
    val newShellRegistry = Registry.get("sbt.new.shell")
    newShellRegistry.setValue(true, getTestRootDisposable)
  }

  def testShellCustomPrompt():Unit = {
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
