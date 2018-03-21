package org.jetbrains.plugins.scala.refactoring.move.member

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
}
