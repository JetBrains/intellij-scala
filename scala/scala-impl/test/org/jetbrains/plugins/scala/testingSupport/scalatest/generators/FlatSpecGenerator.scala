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

  val flatSpecFileName = flatSpecClassName + ".scala"
  val behaviourFlatFileName = behaviourFlatClassName + ".scala"
  val testItFlatFileName = testItFlatClassName + ".scala"

  addSourceFile(flatSpecFileName,
    s"""import org.scalatest._
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
       |
       | it should "run tagged tests" taggedAs(FlatSpecTag) in {}
       |}
       |
       |object FlatSpecTag extends Tag("MyTag")
       |""".stripMargin.trim()
  )

  addSourceFile(behaviourFlatFileName,
    s"""import org.scalatest._
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
       |
       |  it should "tag" taggedAs(BehaviorTag) in {
       |
       |  }
       |}
       |
       |object BehaviorTag extends Tag("MyTag")
       |""".stripMargin.trim()
  )

  addSourceFile(testItFlatFileName,
    s"""import org.scalatest._
       |
       |class $testItFlatClassName extends FlatSpec with GivenWhenThen {
       | it should "run test with correct name" in {
       | }
       |
       | it should "tag" taggedAs(ItTag) in {
       | }
       |
       | "Test" should "be fine" in {}
       |
       | it should "change name" in {}
       |}
       |
       |object ItTag extends Tag("MyTag")
       |""".stripMargin.trim()
  )

}
