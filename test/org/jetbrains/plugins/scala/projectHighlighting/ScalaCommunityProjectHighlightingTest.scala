package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.{DownloadingAndImportingTestCase, ScalaCommunityDownloadingAndImportingTestCase}
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaCommunityProjectHighlightingTest extends DownloadingAndImportingTestCase
  with ScalaCommunityDownloadingAndImportingTestCase
  with AllProjectHighlightingTest {

  //revision: a9ac902 TeamCityServer on 27.08.16 at 0:42 [scala-plugin]
  override def revision: String = "a9ac902e8930c520b390095d9e9346d9ae546212"

  override def getProject = myProject

  def testScalaCommunityHighlighting(): Unit = doAllProjectHighlightingTest()
}
