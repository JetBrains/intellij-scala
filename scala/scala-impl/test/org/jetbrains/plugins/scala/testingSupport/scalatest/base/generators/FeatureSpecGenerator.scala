package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FeatureSpecGenerator extends ScalaTestTestCase {

  protected val featureSpecClassName = "FeatureSpecTest"
  protected val featureSpecFileName = featureSpecClassName + ".scala"

  import featureSpecApi._

  addSourceFile(featureSpecFileName,
    s"""$ImportsForFeatureSpec
       |
       |class $featureSpecClassName extends $FeatureSpecBase with GivenWhenThen {
       | $featureMethodName("Feature 1") {
       |   $scenarioMethodName("Scenario A") {
       |    Given("A")
       |    print("$TestOutputPrefix OK $TestOutputSuffix")
       |   }
       |   $scenarioMethodName("Scenario B") {
       |    Given("B")
       |    print("$TestOutputPrefix 1-B-B $TestOutputSuffix")
       |   }
       | }
       |
       | $featureMethodName("Feature 2") {
       |   $scenarioMethodName("Scenario C") {
       |    Given("C")
       |    print("$TestOutputPrefix 2-C-C $TestOutputSuffix")
       |   }
       | }
       |
       | $featureMethodName("empty") {}
       |
       | $featureMethodName("Feature 3") {
       |   $scenarioMethodName("Tagged", FeatureSpecTag) {}
       | }
       |}
       |
       |object FeatureSpecTag extends Tag("MyTag")
       |""".stripMargin.trim()
  )
}
