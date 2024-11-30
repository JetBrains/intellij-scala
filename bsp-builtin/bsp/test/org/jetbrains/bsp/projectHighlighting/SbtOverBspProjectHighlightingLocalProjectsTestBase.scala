package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.projectHighlighting.base.{AllProjectHighlightingTest, ProjectHighlightingTestUtils}

abstract class SbtOverBspProjectHighlightingLocalProjectsTestBase
  extends SbtOverBspProjectHighlightingTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = myProject

  //Right now I reuse project used for sbt external system tests just for simplicity
  //I want to reuse single test project...
  override def rootProjectsDirPath: String = s"${ProjectHighlightingTestUtils.projectsRootPath}/local"

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}