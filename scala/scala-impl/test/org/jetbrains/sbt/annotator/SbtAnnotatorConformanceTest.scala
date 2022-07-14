package org.jetbrains.sbt
package annotator

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result._
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
    val expression = ScalaPsiElementFactory.createExpressionFromText(text, file)

    val isAllowed = SbtAnnotator.isTypeAllowed(
      expression,
      expression.`type`() match {
        case Right(value) => value
        case Left(failure)  =>
          throw new NoSuchElementException(
            s"""Couldn't infer expression type
              |expression: $expression
              |cause: $failure""".stripMargin
          )
      },
      expected
    )
    assertTrue(s"$expression should conform to $expected", isAllowed)
  }
}

class SbtAnnotatorConformanceTest_0_12_4 extends SbtAnnotatorConformanceTestBase with MockSbt_0_12 {
  override implicit val sbtVersion: Version = Version("0.12.4")

  def testSingleSetting(): Unit = testSingleSetting("Project.Setting[_]")

  def testSeqSettings(): Unit = testSeqSettings("Seq[Project.Setting[_]]")
}

class SbtAnnotatorConformanceTest_0_13_1 extends SbtAnnotatorConformanceTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: Version = Version("0.13.1")

  def testSingleSetting(): Unit = testSingleSetting("Def.SettingsDefinition")

  def testSeqSettings(): Unit = testSeqSettings("Seq[Def.SettingsDefinition]")
}

class SbtAnnotatorConformanceTest_0_13_7 extends SbtAnnotatorConformanceTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: Version = Version("0.13.7")

  def testSingleSetting(): Unit = testSingleSetting("sbt.internals.DslEntry")

  def testSeqSettings(): Unit = testSeqSettings("sbt.internals.DslEntry")
}

class SbtAnnotatorConformanceTest_latest extends SbtAnnotatorConformanceTestBase with MockSbt_1_0 {
  override implicit val sbtVersion: Version = Sbt.LatestVersion

  def testSingleSetting(): Unit = testSingleSetting("sbt.internal.DslEntry")

  def testSeqSettings(): Unit = testSeqSettings("sbt.internal.DslEntry")
}
