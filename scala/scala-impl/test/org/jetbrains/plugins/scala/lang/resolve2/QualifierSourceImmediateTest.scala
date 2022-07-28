package org.jetbrains.plugins.scala.lang.resolve2

class QualifierSourceImmediateTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "qualifier/source/immediate/"
  }

  def testCaseClass(): Unit = doTest()
  //TODO
//  def testCaseClassObject = doTest
  //TODO
//  def testCaseClassObjectSyntetic = doTest
  def testCaseObject(): Unit = doTest()
  //TODO
//  def testCaseObjectSyntetic = doTest
  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}