package org.jetbrains.plugins.scala.injection

import org.jetbrains.plugins.scala.ScalaVersion

abstract class RegexpLanguageInjectionCodeTest extends ScalaLanguageInjectionTestBase {
  def `test scala.util.matching.Regex constructor`(): Unit = {
    scalaInjectionTestFixture.doTest(
      InjectionTestUtils.RegexpLangId,
      """import scala.util.matching.Regex
        |
        |new Regex("[\\d\\w]+.*")""".stripMargin,
      """[\d\w]+.*"""
    )
  }

  def `test scala.util.matching.Regex constructor via type alias`(): Unit = {
    scalaInjectionTestFixture.doTest(
      InjectionTestUtils.RegexpLangId,
      """type MyRegexAlias = scala.util.matching.Regex
        |new MyRegexAlias("[\\d\\w]+.*")
        |""".stripMargin,
      """[\d\w]+.*"""
    )
  }

  def `test custom Regex class`(): Unit = {
    getFixture.configureByText("A.scala",
      """new Regex("[\\d\\w]+.*")
        |
        |class Regex {
        |   def this(regex: String, groupNames: String*) = this()
        |}
        |""".stripMargin,
    )
    scalaInjectionTestFixture.assertHasNoInjectionAtCaret()
  }
}

class RegexpLanguageInjectionCodeTest_Scala211 extends RegexpLanguageInjectionCodeTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_11
}

class RegexpLanguageInjectionCodeTest_Scala212 extends RegexpLanguageInjectionCodeTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12
}

class RegexpLanguageInjectionCodeTest_Scala213 extends RegexpLanguageInjectionCodeTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13
}

class RegexpLanguageInjectionCodeTest_Scala3 extends RegexpLanguageInjectionCodeTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def `test scala.util.matching.Regex constructor universal apply syntax`(): Unit = {
    scalaInjectionTestFixture.doTest(
      InjectionTestUtils.RegexpLangId,
      """import scala.util.matching.Regex
        |
        |Regex("[\\d\\w]+.*")""".stripMargin,
      """[\d\w]+.*"""
    )
  }

  def `test scala.util.matching.Regex constructor universal apply syntax (explicit apply call)`(): Unit = {
    scalaInjectionTestFixture.doTest(
      InjectionTestUtils.RegexpLangId,
      """import scala.util.matching.Regex
        |
        |Regex.apply("[\\d\\w]+.*")""".stripMargin,
      """[\d\w]+.*"""
    )
  }
}