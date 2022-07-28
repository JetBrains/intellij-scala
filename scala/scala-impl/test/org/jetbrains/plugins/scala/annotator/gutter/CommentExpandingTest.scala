package org.jetbrains.plugins.scala.annotator.gutter

class CommentExpandingTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/comment/expanding/"

  def testAfterInLine(): Unit = doTest()
  def testBeforeInLine(): Unit = doTest()
  def testBlockInLine(): Unit = doTest()
  def testBlockOne(): Unit = doTest()
  def testBlockTwo(): Unit = doTest()
  def testJoined(): Unit = doTest()
  def testInitial(): Unit = doTest()
  def testInsideLine(): Unit = doTest()
  def testLineOne(): Unit = doTest()
  def testLineTwo(): Unit = doTest()
  def testMixed(): Unit = doTest()
  def testNextLine(): Unit = doTest()
  def testSeparatedOne(): Unit = doTest()
  def testSeparatedTwo(): Unit = doTest()
  def testSequence1(): Unit = doTest()
  def testSequence2(): Unit = doTest()
//  def testSequence3 = doTest // TODO add trailing comments to corresponding elements
  def testStatement(): Unit = doTest()
}