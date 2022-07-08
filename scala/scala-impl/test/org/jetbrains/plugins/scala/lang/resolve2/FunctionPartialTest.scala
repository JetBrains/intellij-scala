package org.jetbrains.plugins.scala.lang.resolve2

class FunctionPartialTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/partial/"
  }

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