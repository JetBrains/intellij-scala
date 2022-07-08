package org.jetbrains.plugins.scala.annotator.gutter

class GroupLineTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/line/"

  def testAnonymousClasses(): Unit = doTest()
  def testBlocks(): Unit = doTest()
  def testClasses(): Unit = doTest()
  def testFunctionDeclarations(): Unit = doTest()
  def testFunctionDefinitions(): Unit = doTest()
  def testImports(): Unit = doTest()
  def testObjects(): Unit = doTest()
  def testPackages(): Unit = doTest()
  def testPackageContainers(): Unit = doTest()
  def testStatements(): Unit = doTest()
  def testTraits(): Unit = doTest()
  def testTypes(): Unit = doTest()
  def testValues(): Unit = doTest()
  def testVariableDeclarations(): Unit = doTest()
  def testVariableDefinitions(): Unit = doTest()}