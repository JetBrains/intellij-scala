package org.jetbrains.plugins.scala.annotator.gutter

class ElementMultilineTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/element/multiline/"

  def testAnonymousClass(): Unit = doTest()
  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariableDefinition(): Unit = doTest()
}