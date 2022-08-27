package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.projectHighlighting.base.{AllProjectHighlightingTest, SbtProjectHighlightingDownloadingFromGithubTestBase}

abstract class GithubSbtAllProjectHighlightingTest
  extends SbtProjectHighlightingDownloadingFromGithubTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = myProject

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}
