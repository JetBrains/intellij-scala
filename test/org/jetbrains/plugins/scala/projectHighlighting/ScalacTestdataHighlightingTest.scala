package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */
class ScalacTestdataHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter with SeveralFilesHighlightingTest {

  override def getProject = super.getProject

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