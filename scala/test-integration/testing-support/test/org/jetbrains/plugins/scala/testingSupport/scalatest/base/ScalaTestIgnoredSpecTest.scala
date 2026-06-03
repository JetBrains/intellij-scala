package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

trait ScalaTestIgnoredSpecTest extends ScalaTestTestCase {

  protected val ignoredTestPath = TestNodePathWithStatus(Magnitude.IGNORED_INDEX, "[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should be ignored and have proper suffix !!! IGNORED !!!")
  protected val succeededTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should run tests")

  addSourceFile("IgnoredTest.scala",
    s"""$ImportsForFlatSpec
       |
       |class IgnoredTestSpec extends $FlatSpecBase with GivenWhenThen {
       | "An IgnoredTestSpec" should "run tests" in {
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
       | ignore should "be ignored and have proper suffix" in {
       |   print("$TestOutputPrefix FAILED $TestOutputSuffix")
       | }
       |}
       |""".stripMargin.trim()
  )

  def testIgnoredTest(): Unit =
    runTestByLocation(
      loc("IgnoredTest.scala", 2, 7),
      assertConfigAndSettings(_, "IgnoredTestSpec"),
      root => assertResultTreePathsEqualsUnordered(root)(Seq(
        succeededTestPath,
        ignoredTestPath
      ))
    )
}
