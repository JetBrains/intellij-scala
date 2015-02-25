package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FeatureSpecGenerator extends IntegrationTest{
  def addFeatureSpec() {
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
  }
}
