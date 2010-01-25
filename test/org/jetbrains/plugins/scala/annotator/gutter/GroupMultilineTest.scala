package org.jetbrains.plugins.scala.annotator.gutter


/**
 * Pavel.Fatin, 21.01.2010
 */

class GroupMultilineTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/multiline/"

  def testAnonymousClasses = doTest
  def testBlocks = doTest
  def testClasses = doTest
  def testFunctionDefinitions = doTest
  def testObjects = doTest
  def testPackageContainers = doTest
  def testTraits = doTest
  def testValues = doTest
  def testVariableDefinitions = doTest
}