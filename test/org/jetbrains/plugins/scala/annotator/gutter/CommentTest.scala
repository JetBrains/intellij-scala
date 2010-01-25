package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class CommentTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/comment/"

  def testBeforeInLine = doTest
  def testBlockInLine = doTest
  def testBlockOne = doTest
  def testBlockTwo = doTest
  def testCollision = doTest
  def testJoined = doTest
  def testLineOne = doTest
  def testLineTwo = doTest
  def testMixed = doTest
  def testSeparatedOne = doTest
  def testSeparatedTwo = doTest
}