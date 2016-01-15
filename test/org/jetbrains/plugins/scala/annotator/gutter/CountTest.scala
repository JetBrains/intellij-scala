package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class CountTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/count/"

  def testCount0() = doTest()
  def testCount1() = doTest()
  def testCount2() = doTest()
  def testCount3() = doTest()
  def testCount4() = doTest()
}