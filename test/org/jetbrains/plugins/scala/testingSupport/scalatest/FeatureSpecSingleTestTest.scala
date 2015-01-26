package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.{TestByLocationRunner, IntegrationTest}

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FeatureSpecSingleTestTest extends IntegrationTest {
  def testFeatureSpec() {
    addFileToProject("FeatureSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FeatureSpecTest extends FeatureSpec with GivenWhenThen {
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
        |}
      """.stripMargin.trim()
    )

    runTestByLocation(5, 7, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest", "Feature: Feature 1 Scenario: Scenario A"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A") &&
          checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B"),
      debug = true
    )
  }
}
