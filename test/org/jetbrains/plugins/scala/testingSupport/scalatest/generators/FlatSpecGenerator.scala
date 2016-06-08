package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FlatSpecGenerator extends ScalaTestTestCase {
  val flatSpecClassName = "FlatSpecTest"
  val behaviourFlatClassName = "BehaviorFlatSpec"
  val testItFlatClassName = "TestItFlatSpec"

  addSourceFile(s"$flatSpecClassName.scala",
    s"""
      |import org.scalatest._
      |
      |class $flatSpecClassName extends FlatSpec with GivenWhenThen {
      | "A FlatSpecTest" should "be able to run single test" in {
      |   Given("an empty test case")
      |   val resultToPrint = ">>TEST: OK<<"
      |
      |   When("the result line is printed")
      |   print(resultToPrint)
      |
      |   Then("nothing happens in the test")
      |
      |   info("and that's quite right")
      | }
      |
      | it should "not run other tests" in {
      |   print(">>TEST: FAILED<<")
      | }
      |}
    """.stripMargin.trim()
  )

  addSourceFile(s"$behaviourFlatClassName.scala",
    s"""
      |import org.scalatest._
      |
      |class $behaviourFlatClassName extends FlatSpec with GivenWhenThen {
      |  behavior of "FlatSpec"
      |
      |  it should "run scopes" in {
      |
      |  }
      |
      |  it should "do other stuff" in {
      |
      |  }
      |}
    """.stripMargin.trim()
  )

  addSourceFile(s"$testItFlatClassName.scala",
    s"""
      |import org.scalatest._
      |
      |class $testItFlatClassName extends FlatSpec with GivenWhenThen {
      | it should "run test with correct name" in {
      | }
      |
      | "Test" should "be fine" in {}
      |
      | it should "change name" in {}
      |}
    """.stripMargin.trim())

}
