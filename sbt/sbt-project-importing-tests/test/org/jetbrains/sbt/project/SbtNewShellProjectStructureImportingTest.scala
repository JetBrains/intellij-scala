package org.jetbrains.sbt.project

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.{Assert, Ignore}

class SbtNewShellProjectStructureImportingTest extends SbtShellProjectStructureImportingTest {

  override def setUp(): Unit = {
    super.setUp()
    val newShellRegistry = Registry.get("sbt.new.shell")
    newShellRegistry.setValue(true, getTestRootDisposable)
  }

  // Tests with sbt < 1.5.0 are ignored because the new shell will be only available for sbt 1.5.0+

  @Ignore
  override def testSimpleSbt013(): Unit = ()

  @Ignore
  override def testSimpleSbt104(): Unit = ()

  @Ignore
  override def testSimpleSbt116(): Unit = ()

  @Ignore
  override def testSimpleSbt128(): Unit = ()

  @Ignore
  override def testSimpleSbt1313(): Unit = ()

  @Ignore
  override def testSimpleSbt149(): Unit = ()

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
