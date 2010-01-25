package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class ElementLineTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/element/line/"

  def testAnonymousClass = doTest
  def testBlock = doTest
  def testClass = doTest
  def testFunctionDeclaration = doTest
  def testFunctionDefinition = doTest
  def testImport = doTest
  def testObject = doTest
  def testPackage = doTest
  def testPackageContainer = doTest
  def testStatement = doTest
  def testTrait = doTest
  def testType = doTest
  def testValue = doTest
  def testVariableDeclaration = doTest
  def testVariableDefinition = doTest
}