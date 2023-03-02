package org.jetbrains.sbt
package annotator

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.Version
import org.junit.Assert.assertTrue

abstract class SbtAnnotatorConformanceTestBase extends SbtAnnotatorTestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    setSbtVersion(sbtVersion)
  }

  protected final def testSingleSetting(expected: String): Unit =
    doConformanceTest("""name := "someName"""", expected)

  protected final def testSeqSettings(expected: String): Unit =
    doConformanceTest("""Seq(organization := "org", scalaVersion := "2.11.8")""", expected)

  private def doConformanceTest(text: String, expected: String): Unit = {
    // just for the context. we can probably create a context without loading the file?
    val file = loadTestFile()
    val expression = ScalaPsiElementFactory.createExpressionFromText(text, file)(getProject)

    val expressionType = expression.`type`() match {
      case Right(value) => value
      case Left(failure) =>
        throw new NoSuchElementException(
          s"""Couldn't infer expression type
             |expression: $expression
             |cause: $failure""".stripMargin
        )
    }
    val isAllowed = SbtAnnotator.isTypeAllowed(expression, expressionType, expected)
    assertTrue(s"$expression should conform to $expected", isAllowed)
  }
}

class SbtAnnotatorConformanceTest_latest_0_13 extends SbtAnnotatorConformanceTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: Version = Sbt.Latest_0_13

  def testSingleSetting(): Unit = testSingleSetting("sbt.internals.DslEntry")

  def testSeqSettings(): Unit = testSeqSettings("sbt.internals.DslEntry")
}

class SbtAnnotatorConformanceTest_latest extends SbtAnnotatorConformanceTestBase with MockSbt_1_0 {
  override implicit val sbtVersion: Version = Sbt.LatestVersion

  def testSingleSetting(): Unit = testSingleSetting("sbt.internal.DslEntry")

  def testSeqSettings(): Unit = testSeqSettings("sbt.internal.DslEntry")
}
