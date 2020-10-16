package org.jetbrains.plugins.scala.testingSupport.munit

import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

class MUnitGoToSourceTest extends MUnitTestCase {

  private val ClassName = "MUnitGoToSourceTest"
  private val FileName = ClassName + ".scala"

  private val qqq = "\"\"\""

  addSourceFile(FileName,
    s"""import munit.FunSuite
       |
       |class $ClassName extends munit.FunSuite {
       |  test("test single line") {
       |  }
       |
       |  test(${qqq}test 2$qqq) {
       |  }
       |}
       |""".stripMargin
  )

  def testGoTo(): Unit = {
    val runConfig = createTestFromCaretLocation(loc(FileName, 2, 10))
    val runResult = runTestFromConfig(runConfig)
    val testTreeRoot = runResult.requireTestTreeRoot
    val asserts = Seq(
      AssertGoToSourceTest(TestNodePath("[root]"), GoToLocation(FileName, 2)),
      AssertGoToSourceTest(TestNodePath("[root]", s"$ClassName.test single line"), GoToLocation(FileName, 3)),
      AssertGoToSourceTest(TestNodePath("[root]", s"$ClassName.test 2"), GoToLocation(FileName, 6)),
    )
    asserts.foreach(_(testTreeRoot))
  }

  def testGoTo_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    val runConfig = createTestFromCaretLocation(loc(FileName, 2, 10))
    val runResult = runTestFromConfig(runConfig)
    val testTreeRoot = runResult.requireTestTreeRoot
    val asserts = Seq(
      AssertGoToSourceTest(TestNodePath("[root]"), GoToLocation(FileName, 5))
    )
    asserts.foreach(_(testTreeRoot))
  }
}
