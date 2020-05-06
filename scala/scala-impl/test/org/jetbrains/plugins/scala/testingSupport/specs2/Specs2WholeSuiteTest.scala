package org.jetbrains.plugins.scala.testingSupport.specs2

/**
  * @author Roman.Shein
  * @since 11.02.2015.
  */
abstract class Specs2WholeSuiteTest extends Specs2TestCase {
  addSourceFile("SpecificationTest.scala",
    """
      |import org.specs2.mutable.Specification
      |
      |class SpecificationTest extends Specification {
      |  "The 'SpecificationTest'" should {
      |    "run single test" in {
      |      print(">>TEST: OK<<")
      |      1 mustEqual 1
      |    }
      |
      |    "ignore other test" in {
      |      print(">>TEST: FAILED<<")
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
    assertConfigAndSettings(createTestFromLocation(2, 10, "paramConstructorTest.scala"), "paramConstructorTest")
  }

  def testSpecification(): Unit =
    runTestByLocation2(3, 14, "SpecificationTest.scala",
      assertConfigAndSettings(_, "SpecificationTest"),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        Seq("[root]", "SpecificationTest", "The 'SpecificationTest' should", "run single test"),
        Seq("[root]", "SpecificationTest", "The 'SpecificationTest' should", "ignore other test")
      ))
    )
}
