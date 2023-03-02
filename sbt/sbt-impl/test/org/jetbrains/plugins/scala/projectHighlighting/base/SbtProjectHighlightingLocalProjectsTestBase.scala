package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.openapi.project.Project

abstract class SbtProjectHighlightingLocalProjectsTestBase
  extends SbtProjectHighlightingTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = myProject

  override def rootProjectsDirPath: String = s"${ProjectHighlightingTestUtils.projectsRootPath}/local"

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}