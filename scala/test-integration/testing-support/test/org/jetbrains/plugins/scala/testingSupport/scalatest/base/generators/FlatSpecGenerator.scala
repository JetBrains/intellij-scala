package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FlatSpecGenerator extends ScalaTestTestCase {

  protected val flatSpecClassName = "FlatSpecTest"
  protected val behaviourFlatClassName = "BehaviorFlatSpec"
  protected val testItFlatClassName = "TestItFlatSpec"
  protected val testWithIgnoreClassName = "SomeServiceTest"

  protected val flatSpecFileName = flatSpecClassName + ".scala"
  protected val behaviourFlatFileName = behaviourFlatClassName + ".scala"
  protected val testItFlatFileName = testItFlatClassName + ".scala"
  protected val testWithIgnoreFileName = testWithIgnoreClassName + ".scala"

  addSourceFile(flatSpecFileName,
    s"""$ImportsForFlatSpec
       |
       |class $flatSpecClassName extends $FlatSpecBase with GivenWhenThen {
       | "A FlatSpecTest" should "be able to run single test" in {
       |   Given("an empty test case")
       |   val resultToPrint = "$TestOutputPrefix OK $TestOutputSuffix"
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
       |   print("$TestOutputPrefix FAILED $TestOutputSuffix")
       | }
       |
       | it should "run tagged tests" taggedAs(FlatSpecTag) in {}
       |}
       |
       |object FlatSpecTag extends Tag("MyTag")
       |""".stripMargin
  )

  addSourceFile(behaviourFlatFileName,
    s"""$ImportsForFlatSpec
       |
       |class $behaviourFlatClassName extends $FlatSpecBase with GivenWhenThen {
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
       |
       |  private def abc(foo: String) = {
       |    println(foo)
       |  }
       |
       |  behavior of "FlatSpec 2"
       |
       |  it should "run scopes 2 1" in {
       |
       |  }
       |
       |  they should "run scopes 2 2" in {
       |
       |  }
       |}
       |
       |object BehaviorTag extends Tag("MyTag")
       |""".stripMargin
  )

  addSourceFile(testItFlatFileName,
    s"""$ImportsForFlatSpec
       |
       |class $testItFlatClassName extends $FlatSpecBase with GivenWhenThen {
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
       |""".stripMargin
  )

  addSourceFile(testWithIgnoreFileName,
    s"""$ImportsForFlatSpec
       |
       |class $testWithIgnoreClassName extends $FlatSpecBase {
       |  behavior of "SomeService"
       |
       |  it should "do something1" in {}
       |  it should "do something2" in {}
       |  it should "do something3" in {}
       |
       |  ignore should "do something4" in {} // ignore test!
       |
       |  it should "do something5" in {} // cannot run single test under "ignored" test
       |  it should "do something6" in {} // cannot run single test under "ignored" test
       |}""".stripMargin
  )
}
