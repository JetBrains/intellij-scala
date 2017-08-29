package org.jetbrains.sbt.annotator

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.sbt.{MockSbt_0_12, MockSbt_0_13, MockSbt_1_0, Sbt}
import org.jetbrains.sbt.annotator.SbtAnnotator.isTypeAllowed
import org.jetbrains.sbt.language.SbtFileImpl
import org.junit.Assert.assertTrue

abstract class SbtAnnotatorConformanceTestBase extends SbtAnnotatorTestBase {

  // maybe we can construct a context without loading a file
  private lazy val file: SbtFileImpl = loadTestFile()

  override protected def setUp(): Unit = {
    super.setUp()
    setSbtVersion(sbtVersion)
  }

  lazy val singleSetting: ScExpression = code"""name := "someName"""".asInstanceOf[ScExpression]
  lazy val seqSettings: ScExpression = code"""Seq(organization := "org", scalaVersion := "2.11.8")""".asInstanceOf[ScExpression]

  def doConformanceTest(expression: ScExpression, typeNameExpected: String): Unit = {
    val file = loadTestFile() // just for the context. we can probably create a context without loading the file?

    expression.setContext(file, null)

    val isAllowed = isTypeAllowed(expression, expression.getType().get, Seq(typeNameExpected))
    assertTrue(s"$expression should conform to $typeNameExpected", isAllowed)
  }
}

class SbtAnnotatorConformanceTest_0_12_4 extends SbtAnnotatorConformanceTestBase with MockSbt_0_12 {
  override implicit val sbtVersion: String = "0.12.4"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "Project.Setting[_]")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "Seq[Project.Setting[_]]")
}

class SbtAnnotatorConformanceTest_0_13_1 extends SbtAnnotatorConformanceTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: String = "0.13.1"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "Def.SettingsDefinition")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "Seq[Def.SettingsDefinition]")
}

class SbtAnnotatorConformanceTest_0_13_7 extends SbtAnnotatorConformanceTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: String = "0.13.7"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "sbt.internals.DslEntry")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "sbt.internals.DslEntry")
}

class SbtAnnotatorConformanceTest_latest extends SbtAnnotatorConformanceTestBase with MockSbt_1_0 {
  override implicit val sbtVersion: String = Sbt.LatestVersion

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "sbt.internal.DslEntry")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "sbt.internal.DslEntry")
}