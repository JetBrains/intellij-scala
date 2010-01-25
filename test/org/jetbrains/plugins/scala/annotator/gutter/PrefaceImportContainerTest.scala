package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class PrefaceImportContainerTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/preface/import/container/"

  def testBlock = doTest
  def testClass = doTest
  def testFunctionDefinition = doTest
  def testObject = doTest
  def testPackageContainer = doTest
  def testTrait = doTest
}