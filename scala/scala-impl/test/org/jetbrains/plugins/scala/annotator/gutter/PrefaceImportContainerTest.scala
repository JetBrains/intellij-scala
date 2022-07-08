package org.jetbrains.plugins.scala.annotator.gutter

class PrefaceImportContainerTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/preface/import/container/"

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackageContainer(): Unit = doTest()
  def testTrait(): Unit = doTest()
}