package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FeatureSpecGenerator extends ScalaTestTestCase {

  val featureSpecClassName = "FeatureSpecTest"
  val featureSpecFileName = featureSpecClassName + ".scala"

  addSourceFile(featureSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $featureSpecClassName extends FeatureSpec with GivenWhenThen {
      | feature("Feature 1") {
      |   scenario("Scenario A") {
      |    Given("A")
      |    print("$TestOutputPrefix OK $TestOutputSuffix")
      |   }
      |   scenario("Scenario B") {
      |    Given("B")
      |    print("$TestOutputPrefix 1-B-B $TestOutputSuffix")
      |   }
      | }
      |
      | feature("Feature 2") {
      |   scenario("Scenario C") {
      |    Given("C")
      |    print("$TestOutputPrefix 2-C-C $TestOutputSuffix")
      |   }
      | }
      |
      | feature("empty") {}
      |
      | feature("Feature 3") {
      |   scenario("Tagged", FeatureSpecTag) {}
      | }
      |}
      |
      |object FeatureSpecTag extends Tag("MyTag")
    """.stripMargin.trim()
  )
}
