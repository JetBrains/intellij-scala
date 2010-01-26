package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class ContainerTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/container/"
  
  def testAnanymousClass = doTest
  def testBlock = doTest
  def testClass = doTest
  def testFile = doTest
  def testFunctionDefinition = doTest
  def testObject = doTest
  def testPackageContainer = doTest
  def testStatement = doTest
  def testTrait = doTest
}