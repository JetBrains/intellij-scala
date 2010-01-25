package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class CommentExpandingTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/comment/expanding/"

  def testAfterInLine = doTest
  def testBeforeInLine = doTest
  def testBlockInLine = doTest
  def testBlockOne = doTest
  def testBlockTwo = doTest
  def testJoined = doTest
  def testInitial = doTest
  def testInsideLine = doTest
  def testLineOne = doTest
  def testLineTwo = doTest
  def testMixed = doTest
  def testNextLine = doTest
  def testSeparatedOne = doTest
  def testSeparatedTwo = doTest
  def testSequence1 = doTest
  def testSequence2 = doTest
  def testSequence3 = doTest
  def testStatement = doTest
}