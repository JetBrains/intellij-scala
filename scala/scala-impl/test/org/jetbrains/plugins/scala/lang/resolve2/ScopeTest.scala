package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ScopeTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "scope"

  def testBlock(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testInnerBlock(): Unit = doTest()
  //TODO packageobject
//  def testPackageObject = doTest
  def testPackageObjectChild(): Unit = doTest()
  //TODO packageobject
//  def testPackageObjectParent = doTest
  def testTwoBlocks(): Unit = doTest()

  def testDefaultParameterInNextClause(): Unit = {doTest()}
}
