package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FlatSpecGenerator

trait FlatSpecSingleTestTest extends FlatSpecSingleTestTestBase with FlatSpecGenerator {

  protected val flatSpecTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test")

  def testFlatSpec_StringScope(): Unit = {
    def test(lineNumber: Int, offset: Int): Unit =
      doTest(flatSpecClassName)(loc(flatSpecFileName, lineNumber, offset))(
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
    def test(lineNumber: Int, offset: Int)
            (expectedTestName: String, expectedTestPath: TestNodePathWithStatus): Unit =
      doTest(testItFlatClassName)(loc(testItFlatFileName, lineNumber, offset))(
        expectedTestName,
        expectedTestPath
      )

    def test1(lineNumber: Int, offset: Int): Unit =
      test(lineNumber, offset)(
        s"should run test with correct name",
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", testItFlatClassName, "should run test with correct name")
      )

    test1(3, 3)
    test1(3, 7)
    test1(3, 10)
    test1(3, 41)
    test1(4, 1)

    test(6, 5)("should tag", TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", testItFlatClassName, "should tag"))
    test(9, 10)("Test should be fine", TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", testItFlatClassName, "Test", "should be fine"))
    test(11, 10)("Test should change name", TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", testItFlatClassName, "Test", "should change name"))
  }

  def testSelectTestAfterIgnore(): Unit = {
    val fileName = testWithIgnoreFileName
    val className = testWithIgnoreClassName

    val baseName = "SomeService should "
    val basePath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SomeServiceTest", "SomeService")

    // before ignore
    doTest(className)(loc(fileName, 5, 3))(baseName + "do something1", basePath :+ "should do something1")
    doTest(className)(loc(fileName, 6, 3))(baseName + "do something2", basePath :+ "should do something2")
    doTest(className)(loc(fileName, 7, 3))(baseName + "do something3", basePath :+ "should do something3")
    // after ignore
    doTest(className)(loc(fileName, 11, 3))(baseName + "do something5", basePath :+ "should do something5")
    doTest(className)(loc(fileName, 12, 3))(baseName + "do something6", basePath :+ "should do something6")
  }
}
