package org.jetbrains.plugins.scala.annotator.gutter

class ContainerNestedTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/container/nested/"

  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testFunctionDefinitionAndClass(): Unit = doTest()
}