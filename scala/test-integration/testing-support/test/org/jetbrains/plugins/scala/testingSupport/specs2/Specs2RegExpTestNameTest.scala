package org.jetbrains.plugins.scala.testingSupport.specs2

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

abstract class Specs2RegExpTestNameTest extends Specs2TestCase {
  protected val regExpClassName = "SpecsRegExpTest"
  protected val regExpFileName = regExpClassName + ".scala"

  addSourceFile(regExpFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class SpecsRegExpTest extends Specification {
      |  "The RegExpTest" should {
      |    "testtesttest" in {
      |      1 mustEqual 1
      |    }
      |
      |    "test" ! { success }
      |
      |    "testtest" >> { success }
      |  }
      |
      |  "First" should {
      |    "run" ! { success }
      |  }
      |
      |  "Second" should {
      |    "run" ! { success }
      |  }
      |}
    """.stripMargin.trim)

  def testInnerMost(): Unit =
    runTestByLocation(loc(regExpFileName, 8, 10),
      assertConfigAndSettings(_, regExpClassName, "test"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "test"))
      }
    )

  def testMiddle(): Unit =
    runTestByLocation(loc(regExpFileName, 10, 10),
      assertConfigAndSettings(_, regExpClassName, "testtest"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "testtest"))
      })

  def testOuterMost(): Unit =
    runTestByLocation(loc(regExpFileName, 4, 10),
      assertConfigAndSettings(_, regExpClassName, "testtesttest"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "testtesttest"))
      })

  //TODO: enable the test once I find a way to run different tests with same description in specs2
  def __IGNORE_testDifferentScopes(): Unit = {
    runTestByLocation(loc(regExpFileName, 14, 10),
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "First should", "run"))
      })

    runTestByLocation(loc(regExpFileName, 18, 10),
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "Second should", "run"))
      })
  }
}

//TODO: since Specs2 versions 3.x it reports some redundant nodes:
//  TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "First should"),
//  TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "Second should"),
// Even when you run single test.
// The tests are not actually run, just reported.
// Not sure whether it's an issue if Scala Plugin or Specs framework
abstract class Specs2RegExpTestNameTest_SinceSpecs3 extends Specs2RegExpTestNameTest {

  override def testInnerMost(): Unit =
    runTestByLocation(loc(regExpFileName, 8, 10),
      assertConfigAndSettings(_, regExpClassName, "test"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "test"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "First should"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "Second should"),
        ))
      }
    )

  override def testMiddle(): Unit =
    runTestByLocation(loc(regExpFileName, 10, 10),
      assertConfigAndSettings(_, regExpClassName, "testtest"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "testtest"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "First should"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "Second should"),
        ))
      })

  override def testOuterMost(): Unit =
    runTestByLocation(loc(regExpFileName, 4, 10),
      assertConfigAndSettings(_, regExpClassName, "testtesttest"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", regExpClassName, "The RegExpTest should", "testtesttest"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "First should"),
          TestNodePathWithStatus(Magnitude.COMPLETE_INDEX, "[root]", regExpClassName, "Second should"),
        ))
      })

}