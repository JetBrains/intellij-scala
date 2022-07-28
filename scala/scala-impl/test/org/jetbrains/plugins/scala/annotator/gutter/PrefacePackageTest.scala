package org.jetbrains.plugins.scala.annotator.gutter

class PrefacePackageTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/preface/package/"

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunctionDeclaration(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testImport(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackage(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testStatement(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testType(): Unit = doTest()
  def testVariableDeclaration(): Unit = doTest()
  def testVariableDefinition(): Unit = doTest()
}