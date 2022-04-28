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
       |    print("$TestOutputPrefix OK $TestOutputSuffix")
       |   }
       |   Scenario("Scenario B") {
       |    Given("B")
       |    print("$TestOutputPrefix 1-B-B$TestOutputSuffix")
       |   }
       | }
       |
       | Feature("Feature 2") {
       |   Scenario("Scenario C") {
       |    Given("C")
       |    print("$TestOutputPrefix 2-C-C$TestOutputSuffix")
       |   }
       | }
       |
       | Feature("empty") {}
       |
       | Feature("Feature 3") {
       |   Scenario("Tagged", FeatureSpecNewTag) {}
       | }
       |}
       |
       |object FeatureSpecNewTag extends Tag("MyTag")
       |""".stripMargin
}
