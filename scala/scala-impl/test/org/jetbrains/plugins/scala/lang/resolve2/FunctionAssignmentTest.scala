package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionAssignmentTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "assignment"

  def testClash(): Unit = doTest()
  def testClashWithType(): Unit = doTest()
  def testDotAndParentheses1(): Unit = doTest()
  def testDotAndParentheses2(): Unit = doTest()
  def testIllegalNames(): Unit = doTest()
  def testIllegalChars(): Unit = doTest()
  def testIncompatibleReturnType(): Unit = doTest()
  def testIncompatibleType(): Unit = doTest()
  def testInstance(): Unit = doTest()
  def testLegalOpChars(): Unit = doTest()
  def testNotAssignmentOrdinary(): Unit = doTest()
  def testPostfix(): Unit = doTest()
  def testOpChar(): Unit = doTest()
  def testOpChars(): Unit = doTest()
  def testParentheses(): Unit = doTest()
  def testStartsWithEqual(): Unit = doTest()
  def testTwoArguments(): Unit = doTest()
  def testValue(): Unit = doTest()
}
