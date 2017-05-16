package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

/**
  * @author mutcianm
  * @since 16.05.17.
  */
abstract class GithubSbtAllProjectHighlightingTest extends DownloadingAndImportingTestCase with AllProjectHighlightingTest {
  override def getProject: Project = myProject
  override protected def getExternalSystemConfigFileName: String = "build.sbt"
  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}
