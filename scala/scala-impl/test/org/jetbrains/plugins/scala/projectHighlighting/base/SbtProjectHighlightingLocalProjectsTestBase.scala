package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.TestUtils

abstract class SbtProjectHighlightingLocalProjectsTestBase extends SbtProjectHighlightingTestBase with AllProjectHighlightingTest {
  override def getProject: Project = myProject

  override def rootProjectsDirPath: String = s"${TestUtils.getTestDataPath}/projectsForHighlightingTests/local"

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}