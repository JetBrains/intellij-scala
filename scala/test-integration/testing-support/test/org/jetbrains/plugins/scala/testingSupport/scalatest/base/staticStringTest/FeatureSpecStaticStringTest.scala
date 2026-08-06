package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FeatureSpecStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "FeatureSpecStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForFeatureSpec
      |
      |class $ClassName extends $FeatureSpecBase {
      | val b = " B"
      | val c = "C"
      | feature("Feature" + " 1") {
      |   scenario("Scenario A") {
      |   }
      |   scenario("Scenario" + b) {
      |   }
      | }
      |
      | feature(c) {
      |   scenario("Scenario C" + System.currentTimeMillis()) {
      |   }
      |   scenario("other") {}
      | }
      |
      | feature("invalid") {
      |   scenario("Failed " + System.currentTimeMillis()) {}
      | }
      |}
      |""".stripMargin
  )

  def testFeatureSpecSumString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, FileName), ClassName,
      "Feature: Feature 1 Scenario: Scenario A")
  }

  def testFeatureSpecValSumString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 7, FileName), ClassName,
      "Feature: Feature 1 Scenario: Scenario B")
  }

  def testFeatureSpecValString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(15, 7, FileName), ClassName,
      "Feature: C Scenario: other")
  }

  def testFeatureSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(13, 7, FileName), ClassName,
      "Feature: C Scenario: other")
    assertConfigAndSettings(createTestCaretLocation(19, 7, FileName), ClassName)
  }
}
