package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import java.nio.file.Path

//See SCL-12414
// TODO 1: the tests should be run for 2_13 as well
// TODO 2: there should be an easy way to update the test data
//  (to the latest scala minor version in the corresponding version branch)
class ScalaCompilerTestdataHighlightingTest_2_12 extends ScalaCompilerTestdataHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override protected def getTestDirName: String = "pos"

  override protected lazy val projectJdk: Sdk =
    SmartJDKLoader.createFilteredJdk(LanguageLevel.JDK_17, Seq("java.base", "java.desktop", "java.rmi"))

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  //NOTE: there is also one patched file scalacTests/pos/t0674.scala
  //TODO: revert the file patch after SCL-20539 is fixed
  private val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "pos/t2994a.scala" -> Set(
      (636,639), // Type constructor m#a does not conform to n[_[_], _]
      (641,642), // Type constructor s does not conform to s[_]
    ),
    "pos/unchecked-a.scala" -> Set(
      (107,110), // Type Any does not conform to upper bound Y of type parameter A
    ),
    // this file is actually an error in Scala > 2.10, so I just ignore it here
    "pos/implicit-anyval-2.10.scala" -> Set(
      (20,26), // Cannot upcast String to AnyVal
    ),
  )

  override protected val reporter: HighlightingProgressReporter =
    HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)

  private def allPosTestFilesToHighlight: Seq[Path] = {
    val dir = Path.of(getScalaCompilerTestDataRoot, "pos")
    dir.children()
  }

  //SOE at pos/t0674.scala
  def testScalacTests(): Unit = doTest(allPosTestFilesToHighlight)
}
