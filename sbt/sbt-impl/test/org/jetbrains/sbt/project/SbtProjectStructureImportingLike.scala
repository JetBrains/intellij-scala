package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions


class SbtProjectStructureImportingLike extends SbtExternalSystemImportingTestLike
  with ProjectStructureMatcher
  with ExactMatch {

  import ProjectStructureDsl._
  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
  }

  protected def runTest(expected: project): Unit = {
    importProject(false)

    assertProjectsEqual(expected, myProject)(ProjectComparisonOptions.Implicit.default)
    assertNoNotificationsShown(myProject)
  }
}
