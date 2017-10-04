package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
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
      |    print(">>TEST: OK<<")
      |   }
      |   scenario("Scenario B") {
      |    Given("B")
      |    print(">>TEST: 1-B-B<<")
      |   }
      | }
      |
      | feature("Feature 2") {
      |   scenario("Scenario C") {
      |    Given("C")
      |    print(">>TEST: 2-C-C<<")
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
