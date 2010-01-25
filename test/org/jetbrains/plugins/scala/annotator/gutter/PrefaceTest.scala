package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class PrefaceTest extends AbstractLineMarkerTest {
  protected override def getBasePath = super.getBasePath + "/preface/"

  def testClassAndPackage = doTest
  def testClassAndPackageContainer = doTest
  def testPackageContainerAndClass = doTest
  def testImportInBetween = doTest
}