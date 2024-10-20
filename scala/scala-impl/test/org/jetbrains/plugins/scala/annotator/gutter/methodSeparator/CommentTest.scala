package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

class CommentTest extends MethodSeparatorLineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/comment/"

  def testBeforeInLine(): Unit = doTest()
  def testBlockInLine(): Unit = doTest()
  def testBlockOne(): Unit = doTest()
  def testBlockTwo(): Unit = doTest()
  def testCollision(): Unit = doTest()
  def testJoined(): Unit = doTest()
  def testLineOne(): Unit = doTest()
  def testLineTwo(): Unit = doTest()
  def testMixed(): Unit = doTest()
  def testSeparatedOne(): Unit = doTest()
  def testSeparatedTwo(): Unit = doTest()
}