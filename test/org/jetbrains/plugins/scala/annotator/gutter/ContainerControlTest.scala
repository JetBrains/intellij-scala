package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class ContainerControlTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/container/control/"

  def testDo = doTest
  def testFor = doTest
  def testIf = doTest
  def testIterator = doTest
  def testMatch = doTest
  def testTry = doTest
  def testWhile = doTest
}