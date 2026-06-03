package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

class PrefaceImportContainerTest extends MethodSeparatorLineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/preface/import/container/"

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testTrait(): Unit = doTest()
}