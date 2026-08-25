package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters._

class SbtTerminalPropsProjectImportingTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getTestDataProjectPath: String =
    TestUtils.findCommunityRootPath.resolve("scala/scala-impl/testdata/sbt/projects/simple").toString

  def testSeparateProcessImportWithSbtTerminalProps(): Unit = {
    SbtSettings.getInstance(getMyProject).setSbtEnvironment(
      Map("SBT_TERMINAL_PROPS" -> "0,0,false,false,false").asJava
    )

    importProject(false)

    assertTrue("The separate sbt process did not import the simple module", getMyProject.modules.exists(_.getName == "simple"))
  }
}
