package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class GroupTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/"

  def testSolid = doTest
  def testSeparated = doTest
  def testMixed = doTest
  def testMixedLine = doTest
  def testStatement = doTest
}