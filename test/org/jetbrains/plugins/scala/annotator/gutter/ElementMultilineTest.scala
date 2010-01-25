package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class ElementMultilineTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/element/multiline/"

  def testAnonymousClass = doTest
  def testBlock = doTest
  def testClass = doTest
  def testFunctionDefinition = doTest
  def testObject = doTest
  def testPackageContainer = doTest
  def testTrait = doTest
  def testValue = doTest
  def testVariableDefinition = doTest
}