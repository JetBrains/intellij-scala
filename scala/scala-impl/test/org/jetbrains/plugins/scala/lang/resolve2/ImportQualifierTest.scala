package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportQualifierTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "qualifier"

  def testImport1(): Unit = doTest()
  def testImport2(): Unit = doTest()
  def testValue1(): Unit = doTest()
  def testValue2(): Unit = doTest()
  //TODO
//  def testValueCaseClass = doTest
  def testValueClass(): Unit = doTest()
  def testVariable(): Unit = doTest()
  def testFunction(): Unit = doTest()
}
