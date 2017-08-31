package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import org.jetbrains.plugins.scala.ScalacTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */

@Category(Array(classOf[ScalacTests]))
class ScalacTestdataHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter with SeveralFilesHighlightingTest {

  override implicit val version: ScalaVersion = Scala_2_12

  override def getProject = super.getProject

  override val reporter = ProgressReporter.newInstance(reportSuccess = false)

  override def filesToHighlight: Array[File] = {
    val testDataPath = TestUtils.getTestDataPath + "/scalacTests/pos/"

    val dir = new File(testDataPath)
    dir.listFiles()
  }

  override def librariesLoaders = Seq(
    ScalaLibraryLoader(isIncludeReflectLibrary = true),
    JdkLoader()
  )

  def testScalacTests(): Unit = doTest()

}