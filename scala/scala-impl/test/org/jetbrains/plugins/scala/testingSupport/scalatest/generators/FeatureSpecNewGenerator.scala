package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

// from 3.1.0
trait FeatureSpecNewGenerator extends ScalaTestTestCase {

  val featureSpecNewClassName = "FeatureSpecNewTest"
  val featureSpecNewFileName = featureSpecNewClassName + ".scala"

  addSourceFile(featureSpecNewFileName, featureSpecNewSourceCode)

  protected def featureSpecNewSourceCode: String =
    s"""import org.scalatest.featurespec.AnyFeatureSpec; import org.scalatest._
       |
       |class $featureSpecNewClassName extends AnyFeatureSpec with GivenWhenThen {
       | Feature("Feature 1") {
       |   Scenario("Scenario A") {
       |    Given("A")
       |    print(">>TEST: OK<<")
       |   }
       |   Scenario("Scenario B") {
       |    Given("B")
       |    print(">>TEST: 1-B-B<<")
       |   }
       | }
       |
       | Feature("Feature 2") {
       |   Scenario("Scenario C") {
       |    Given("C")
       |    print(">>TEST: 2-C-C<<")
       |   }
       | }
       |
       | Feature("empty") {}
       |
       | Feature("Feature 3") {
       |   Scenario("Tagged", FeatureSpecTag) {}
       | }
       |}
       |
       |object FeatureSpecTag extends Tag("MyTag")
       |""".stripMargin
}
