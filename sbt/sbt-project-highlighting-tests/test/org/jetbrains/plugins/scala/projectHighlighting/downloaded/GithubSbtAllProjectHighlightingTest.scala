package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ProjectHighlightingTests
import org.jetbrains.plugins.scala.projectHighlighting.base.{AllProjectHighlightingTest, SbtProjectHighlightingDownloadingFromGithubTestBase}
import org.junit.experimental.categories.Category

@Category(Array(classOf[ProjectHighlightingTests]))
abstract class GithubSbtAllProjectHighlightingTest
  extends SbtProjectHighlightingDownloadingFromGithubTestBase
    with AllProjectHighlightingTest {

  override def getProject: Project = getMyProject

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}
