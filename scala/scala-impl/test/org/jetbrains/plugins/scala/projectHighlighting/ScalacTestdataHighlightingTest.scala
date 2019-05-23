package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.ScalacTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */

@Category(Array(classOf[ScalacTests]))
class ScalacTestdataHighlightingTest extends ScalacTestdataHighlightingTestBase {

  override val reporter = ProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems = Map.empty, reportStatus = false)

  override def filesToHighlight: Array[File] = {
    val testDataPath = TestUtils.getTestDataPath + "/scalacTests/pos/"

    val dir = new File(testDataPath)
    dir.listFiles()
  }

  def testScalacTests(): Unit = doTest()

}

abstract class ScalacTestdataHighlightingTestBase
  extends ScalaLightCodeInsightFixtureTestAdapter with SeveralFilesHighlightingTest  {

  override def getProject = super.getProject

  override def getModule: Module = super.getModule

  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders = Seq(
    ScalaSDKLoader(includeScalaReflect = true)
  )
}