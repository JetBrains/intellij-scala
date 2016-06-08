package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 24.06.2015.
  */
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

  def testFeatureSpecSumString() = {
    assert(checkConfigAndSettings(createTestFromLocation(6, 7, featureSpecFileName), featureSpecClassName,
      "Feature: Feature 1 Scenario: Scenario A"))
  }

  def testFeatureSpecValSumString() = {
    assert(checkConfigAndSettings(createTestFromLocation(8, 7, featureSpecFileName), featureSpecClassName,
      "Feature: Feature 1 Scenario: Scenario B"))
  }

  def testFeatureSpecValString() = {
    assert(checkConfigAndSettings(createTestFromLocation(15, 7, featureSpecFileName), featureSpecClassName,
      "Feature: C Scenario: other"))
  }

  def testFeatureSpecNonConst() = {
    assert(checkConfigAndSettings(createTestFromLocation(13, 7, featureSpecFileName), featureSpecClassName,
      "Feature: C Scenario: other"))
    assert(checkConfigAndSettings(createTestFromLocation(19, 7, featureSpecFileName), featureSpecClassName))
  }
}
