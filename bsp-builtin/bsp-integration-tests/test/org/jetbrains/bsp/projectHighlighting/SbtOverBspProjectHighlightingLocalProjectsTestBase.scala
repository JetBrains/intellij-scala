package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.sbt.SbtTestDataUtils

abstract class SbtOverBspProjectHighlightingLocalProjectsTestBase
  extends SbtOverBspProjectHighlightingTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = getMyProject

  override def rootProjectsDirPath: String =
    SbtTestDataUtils.resolveRelativePath(
      "sbt-project-highlighting-tests/testdata/projectsForHighlightingTests/local"
    )

  // The BSP connection file is generated in `SbtOverBspExternalSystemImportingTestCase.setUp`.
  // If the import happened earlier in `ScalaProjectHighlightingTestBase`, it could happen that no BSP connection file would be present.
  override protected def importProjectDuringTestSetup = false

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = {
    importProject(false)
    doAllProjectHighlightingTest()
  }
}
