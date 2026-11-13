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

  /**
   * The test projects are part of this repository and declare no resolvers of their own. Without the override, sbt
   * resolves the sbt plugins of a build from repo1.maven.org, whatever the repositories file says. On the macOS CI
   * agents, loading the plugins of the sbt 2 project matrix builds that way with empty caches took longer than the
   * 10-minute limit of sbt imports in tests (SCL-25750).
   */
  override protected def overrideBuildRepositories: Boolean = true

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}
