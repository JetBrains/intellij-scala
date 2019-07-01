package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FlatSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FlatSpecSingleTestTest extends FlatSpecSingleTestTestBase with FlatSpecGenerator {
  val flatSpecTestPath = List("[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test")

  def testFlatSpec_StringScope(): Unit = {
    def test(lineNumber: Int, offset: Int): Unit = doTest(flatSpecFileName, flatSpecClassName)(lineNumber, offset)(
      "A FlatSpecTest should be able to run single test",
      flatSpecTestPath
    )

    test(4, 3)
    test(4, 30)
    test(4, 60)
    test(4, 64)
    test(7, 1)
  }

  def testFlatSpec_ItWithoutExplicitScope(): Unit = {
    def test(lineNumber: Int, offset: Int)(expectedTestName: String, expectedTestPath: List[String]): Unit =
      doTest(testItFlatFileName, testItFlatClassName)(lineNumber, offset)(expectedTestName, expectedTestPath)

    def test1(lineNumber: Int, offset: Int): Unit =
      test(lineNumber, offset)(
        s"should run test with correct name",
        List("[root]", testItFlatClassName, "should run test with correct name")
      )

    test1(3, 3)
    test1(3, 7)
    test1(3, 10)
    test1(3, 41)
    test1(4, 1)

    test(6, 5)("should tag", List("[root]", testItFlatClassName, "should tag"))
    test(9, 10)("Test should be fine", List("[root]", testItFlatClassName, "Test", "should be fine"))
    test(11, 10)("Test should change name", List("[root]", testItFlatClassName, "Test", "should change name"))
  }
}
