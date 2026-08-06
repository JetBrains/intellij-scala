package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionTypeTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "type"

  def testChoiceOne(): Unit = doTest()
  def testChoiceTwo(): Unit = doTest()
  def testIncompatible(): Unit = doTest()
  def testIncompatibleFirst(): Unit = doTest()
  def testIncompatibleSecond(): Unit = doTest()
  def testIncompatibleWithCount(): Unit = doTest()
  def testInheritanceChild(): Unit = doTest()
  def testInheritanceParent(): Unit = doTest()
  def testParentheses(): Unit = doTest()
}
