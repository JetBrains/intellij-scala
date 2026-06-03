package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.openapi.project.Project
import org.jetbrains.sbt.SbtTestDataUtils

abstract class SbtProjectHighlightingLocalProjectsTestBase
  extends SbtProjectHighlightingTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = getMyProject

  override def rootProjectsDirPath: String =
    SbtTestDataUtils.resolveRelativePath(
      "sbt-project-highlighting-tests/testdata/projectsForHighlightingTests/local"
    )

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}
