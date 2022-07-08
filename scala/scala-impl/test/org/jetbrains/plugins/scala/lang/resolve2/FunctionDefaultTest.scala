package org.jetbrains.plugins.scala.lang.resolve2

class FunctionDefaultTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/default/"
  }

  def testFirstAsOne(): Unit = doTest()
  def testFirstAsTwo(): Unit = doTest()
  def testImplicitExists(): Unit = doTest()
  def testImplicitNotExists(): Unit = doTest()
  def testOneAsEmpty(): Unit = doTest()
  def testOneAsIncompatible(): Unit = doTest()
  def testOneAsNone(): Unit = doTest()
  def testOneAsOne(): Unit = doTest()
  def testOneAsTwo(): Unit = doTest()
  def testSecondAsEmpty(): Unit = doTest()
  def testSecondAsNone(): Unit = doTest()
  def testSecondAsOne(): Unit = doTest()
  def testSecondAsThree(): Unit = doTest()
  def testSecondAsTwo(): Unit = doTest()
  def testTwoAsEmpty(): Unit = doTest()
  def testTwoAsNone(): Unit = doTest()
  def testTwoAsOne(): Unit = doTest()
  def testTwoAsThree(): Unit = doTest()
  def testTwoAsTwo(): Unit = doTest()
}