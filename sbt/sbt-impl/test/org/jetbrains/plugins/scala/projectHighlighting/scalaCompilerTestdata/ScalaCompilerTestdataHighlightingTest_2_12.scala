package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.SdkConfiguration
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import java.io.File

//See SCL-12414
// TODO 1: the tests should be run for 2_13 as well
// TODO 2: there should be an easy way to update the test data
//  (to the latest scala minor version in the corresponding version branch)
class ScalaCompilerTestdataHighlightingTest_2_12 extends ScalaCompilerTestdataHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override protected def getTestDirName: String = "pos"

  override protected def sdkConfiguration: SdkConfiguration = SdkConfiguration.FullJdk

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  //NOTE: there is also one patched file scalacTests/pos/t0674.scala
  //TODO: revert the file patch after SCL-20539 is fixed
  private val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "pos/t2994a.scala" -> Set((636, 639), (641, 642)),
    "pos/unchecked-a.scala" -> Set((107, 110))
  )

  override protected val reporter: HighlightingProgressReporter =
    HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)

  override protected def filesToHighlight: Seq[File] = {
    val testDataPath = s"${TestUtils.getTestDataPath}/scalacTests/pos/"

    val dir = new File(testDataPath)
    dir.listFiles().toSeq
  }

  //SOE at pos/t0674.scala
  def testScalacTests(): Unit = doTest()
}
