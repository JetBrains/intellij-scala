package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2WholeSuiteTest extends Specs2TestCase {
  addSourceFile("SpecificationTest.scala",
    s"""
      |import org.specs2.mutable.Specification
      |
      |class SpecificationTest extends Specification {
      |  "The 'SpecificationTest'" should {
      |    "run single test" in {
      |      print("$TestOutputPrefix OK $TestOutputSuffix")
      |      1 mustEqual 1
      |    }
      |
      |    "ignore other test" in {
      |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin
  )

  addSourceFile("paramConstructorTest.scala",
    s"""
       |import org.specs2.mutable.Specification
       |
       |class paramConstructorTest(implicit someParam: Object) extends Specification
    """.stripMargin
  )

  def testParamConstructor(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(2, 10, "paramConstructorTest.scala"), "paramConstructorTest")
  }

  def testSpecification(): Unit =
    runTestByLocation(loc("SpecificationTest.scala", 3, 14),
      assertConfigAndSettings(_, "SpecificationTest"),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        TestNodePath("[root]", "SpecificationTest", "The 'SpecificationTest' should", "run single test"),
        TestNodePath("[root]", "SpecificationTest", "The 'SpecificationTest' should", "ignore other test")
      ))
    )
}
