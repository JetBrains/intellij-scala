package org.jetbrains.plugins.scala.annotator.gutter

/**
 * Pavel.Fatin, 21.01.2010
 */

class PrefaceTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/preface/"

  def testClassAndPackage(): Unit = doTest()
  def testClassAndPackageContainer(): Unit = doTest()
  def testPackageContainerAndClass(): Unit = doTest()
  def testImportInBetween(): Unit = doTest()
}