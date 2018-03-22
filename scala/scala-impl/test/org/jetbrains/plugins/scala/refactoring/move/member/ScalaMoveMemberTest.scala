package org.jetbrains.plugins.scala.refactoring.move.member

import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import org.junit.Assert.fail

class ScalaMoveMemberTest extends BaseScalaMoveMemberTest {

  def testVal() = {
    doTest("A$", "B$", "x")
  }

  def testVar() = {
    doTest("A$", "B$", "x")
  }

  def testDef() = {
    doTest("A$", "B$", "x")
  }

  def testConflict() = {
    try {
      doTest("A$", "B$", "x")
      fail("expected 'ConflictsInTestsException'")
    } catch {
      case _:ConflictsInTestsException =>
      case _ => fail("expected 'ConflictsInTestsException'")
    }
  }
}
