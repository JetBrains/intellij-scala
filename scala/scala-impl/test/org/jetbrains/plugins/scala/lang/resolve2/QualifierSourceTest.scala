package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class QualifierSourceTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "qualifier" / "source"

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
