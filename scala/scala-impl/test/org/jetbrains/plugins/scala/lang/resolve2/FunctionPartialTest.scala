package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionPartialTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "partial"

  def testAllToEmpty(): Unit = doTest()
  def testAllToNone(): Unit = doTest()
  def testAllToOne(): Unit = doTest()
  def testAllToTwo(): Unit = doTest()
  def testAppliedFirst(): Unit = doTest()
  def testAppliedMany(): Unit = doTest()
  def testAppliedSecond(): Unit = doTest()
  def testOneToEmpty(): Unit = doTest()
  def testOneToNone(): Unit = doTest()
  def testOneToOne(): Unit = doTest()
  def testOneToTwo(): Unit = doTest()
  def testTwoToOne(): Unit = doTest()
  def testTwoToTwo(): Unit = doTest()
  def testTypeIncompatible(): Unit = doTest()
  def testTypeInheritance(): Unit = doTest()
  def testTypeInheritanceIncompatible(): Unit = doTest()
}
