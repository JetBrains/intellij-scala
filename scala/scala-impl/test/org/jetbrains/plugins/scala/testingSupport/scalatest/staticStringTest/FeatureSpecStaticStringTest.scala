package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FeatureSpecStaticStringTest extends ScalaTestTestCase {

  val featureSpecClassName = "FeatureSpecStringTest"
  val featureSpecFileName = featureSpecClassName + ".scala"

  addSourceFile(featureSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $featureSpecClassName extends FeatureSpec {
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
    """.stripMargin.trim()
  )

  def testFeatureSpecSumString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, featureSpecFileName), featureSpecClassName,
      "Feature: Feature 1 Scenario: Scenario A")
  }

  def testFeatureSpecValSumString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 7, featureSpecFileName), featureSpecClassName,
      "Feature: Feature 1 Scenario: Scenario B")
  }

  def testFeatureSpecValString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(15, 7, featureSpecFileName), featureSpecClassName,
      "Feature: C Scenario: other")
  }

  def testFeatureSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(13, 7, featureSpecFileName), featureSpecClassName,
      "Feature: C Scenario: other")
    assertConfigAndSettings(createTestCaretLocation(19, 7, featureSpecFileName), featureSpecClassName)
  }
}
