package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierSourceTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "qualifier/source/"
  }

  def testChainLong() = doTest()
  def testChainDeep() = doTest()
  def testPackage() = doTest()
  //TODO getClass
//  def testPackageAsValue = doTest
  //TODO packageobject
//  def testPackageObject = doTest
  //TODO packageobject
//  def testPackageObjectAsValue = doTest
}