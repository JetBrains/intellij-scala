package org.jetbrains.sbt
package annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

abstract class SbtAnnotatorConformanceTestBase(
  sbtVersion: SbtVersion,
  defaultScalaVersion: ScalaVersion,
  supportedIn: ScalaVersion => Boolean
) extends SbtAnnotatorTestBase(sbtVersion, defaultScalaVersion, supportedIn):

  protected final def doTestSingleSetting(expected: String): Unit =
    doConformanceTest("""name := "someName"""", expected)

  protected final def doTestSeqSettings(expected: String): Unit =
    doConformanceTest("""Seq(organization := "org", scalaVersion := "2.11.8")""", expected)

  private def doConformanceTest(text: String, expected: String): Unit =
    withSbtProjectSetUp:
      inReadAction:
        // just for the context. we can probably create a context without loading the file?
        val file = loadTestFile()
        val expression = ScalaPsiElementFactory.createExpressionFromText(text, file)(using getProject)

        val expressionType = expression.`type`() match
          case Right(value) => value
          case Left(failure) =>
            throw new NoSuchElementException(
              s"""Couldn't infer expression type
                 |expression: $expression
                 |cause: $failure""".stripMargin
            )
        val isAllowed = SbtAnnotator.isTypeAllowed(expression, expressionType, expected)
        assertTrue(isAllowed, s"$expression should conform to $expected")
end SbtAnnotatorConformanceTestBase

class SbtAnnotatorConformanceTest_latest_0_13 extends SbtAnnotatorConformanceTestBase(
  SbtVersion.Latest.Sbt_0_13,
  ScalaVersion.Latest.Scala_2_10,
  _ <= ScalaVersion.Latest.Scala_2_10
):
  @Test
  def testSingleSetting(): Unit = doTestSingleSetting("sbt.internals.DslEntry")

  @Test
  def testSeqSettings(): Unit = doTestSeqSettings("sbt.internals.DslEntry")

class SbtAnnotatorConformanceTest_latest_1 extends SbtAnnotatorConformanceTestBase(
  SbtVersion.Latest.Sbt_1,
  ScalaVersion.Latest.Scala_2_12,
  _ >= ScalaVersion.Latest.Scala_2_12
):
  @Test
  def testSingleSetting(): Unit = doTestSingleSetting("sbt.internal.DslEntry")

  @Test
  def testSeqSettings(): Unit = doTestSeqSettings("sbt.internal.DslEntry")

class SbtAnnotatorConformanceTest_latest_2 extends SbtAnnotatorConformanceTestBase(
  SbtVersion.Latest.Sbt_2,
  ScalaVersion.Latest.Scala_3,
  _.isScala3
):
  @Test
  def testSingleSetting(): Unit = doTestSingleSetting("sbt.internal.DslEntry")

  @Test
  def testSeqSettings(): Unit = doTestSeqSettings("sbt.internal.DslEntry")
