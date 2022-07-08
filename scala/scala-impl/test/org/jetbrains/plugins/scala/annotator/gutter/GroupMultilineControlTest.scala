package org.jetbrains.plugins.scala.annotator.gutter

class GroupMultilineControlTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/multiline/control/"

  def testDo(): Unit = doTest()
  def testFor(): Unit = doTest()
  def testIf(): Unit = doTest()
  def testIterator(): Unit = doTest()
  def testMatch(): Unit = doTest()
  def testTry(): Unit = doTest()
  def testWhile(): Unit = doTest()
}