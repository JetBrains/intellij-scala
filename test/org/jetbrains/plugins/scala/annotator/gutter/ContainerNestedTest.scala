package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class ContainerNestedTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/container/nested/"

  def testClass = doTest
  def testObject = doTest
  def testPackageContainer = doTest
  def testTrait = doTest
  def testFunctionDefinitionAndClass = doTest
}