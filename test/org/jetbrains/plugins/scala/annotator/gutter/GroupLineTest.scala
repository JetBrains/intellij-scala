package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class GroupLineTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/group/line/"

  def testAnonymousClasses = doTest
  def testBlocks = doTest
  def testClasses = doTest
  def testFunctionDeclarations = doTest
  def testFunctionDefinitions = doTest
  def testImports = doTest
  def testObjects = doTest
  def testPackages = doTest
  def testPackageContainers = doTest
  def testStatements = doTest
  def testTraits = doTest
  def testTypes = doTest
  def testValues = doTest
  def testVariableDeclarations = doTest
  def testVariableDefinitions = doTest}