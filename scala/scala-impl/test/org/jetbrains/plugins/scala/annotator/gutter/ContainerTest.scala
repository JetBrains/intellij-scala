package org.jetbrains.plugins.scala.annotator.gutter

class ContainerTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/container/"
  
  def testAnanymousClass(): Unit = doTest()
  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFile(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testStatement(): Unit = doTest()
  def testTrait(): Unit = doTest()
}