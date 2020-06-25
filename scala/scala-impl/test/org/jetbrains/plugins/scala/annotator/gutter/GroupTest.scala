package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class GroupTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/"

  def testSolid(): Unit = doTest()
  def testSeparated(): Unit = doTest()
  def testMixed(): Unit = doTest()
  def testMixedLine(): Unit = doTest()
  def testStatement(): Unit = doTest()
}