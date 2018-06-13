package org.jetbrains.plugins.scala.refactoring.move.member

import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import org.junit.Assert.fail

class ScalaMoveMemberTest extends BaseScalaMoveMemberTest {

  def testVal(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  val x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  val x = 1
        |}
      """.stripMargin
    )
  }

  def testVar(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  var x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  var x = 1
        |}
      """.stripMargin
    )

  }

  def testDef(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  def x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  def x = 1
        |}
      """.stripMargin
    )

  }

  def testConflict(): Unit = {
    try {
      doTest("A", "B", "x",
        """
          |object A {
          |  def x = 1
          |}
          |
          |object B {
          |  def x = 1
          |}
        """.stripMargin,
        null
      )
      fail("expected 'ConflictsInTestsException'")
    } catch {
      case _:ConflictsInTestsException =>
      case _ => fail("expected 'ConflictsInTestsException'")
    }
  }
}
