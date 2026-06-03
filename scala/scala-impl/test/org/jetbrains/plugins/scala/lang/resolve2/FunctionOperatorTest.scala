package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionOperatorTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "operator"

  def testDot(): Unit = doTest()
  def testDotAndParentheses(): Unit = doTest()
  def testNameArbitrary(): Unit = doTest()
  def testNameLong(): Unit = doTest()
  //TODO
//  def testParametersEmpty = doTest
  //TODO
//  def testParametersNone = doTest
  def testParametersTwo(): Unit = doTest()
  def testParametersType(): Unit = doTest()
  def testParentheses(): Unit = doTest()
  def testQualifierInstance(): Unit = doTest()
  //TODO
//  def testQualifierNone = doTest
  def testQualifierObject(): Unit = doTest()
  //TODO
//  def testQualifierThis = doTest
}
