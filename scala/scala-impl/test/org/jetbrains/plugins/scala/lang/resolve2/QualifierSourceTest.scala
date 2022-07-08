package org.jetbrains.plugins.scala.lang.resolve2

class QualifierSourceTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "qualifier/source/"
  }

  def testChainLong(): Unit = doTest()
  def testChainDeep(): Unit = doTest()
  def testPackage(): Unit = doTest()
  //TODO getClass
//  def testPackageAsValue = doTest
  //TODO packageobject
//  def testPackageObject = doTest
  //TODO packageobject
//  def testPackageObjectAsValue = doTest
}