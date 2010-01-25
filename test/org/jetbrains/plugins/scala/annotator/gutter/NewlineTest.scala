package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class NewlineTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/newline/"

  def testCollision = doTest
  def testJoined = doTest
  def testSpacing = doTest
  def testWrap = doTest
}