package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class BasicTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "basic"

  def testSimple(): Unit = doTest()
  def testMultipleDeclaration(): Unit = doTest()
  def testName(): Unit = doTest()
  def testToPattern(): Unit = doTest()
  def testGetClass(): Unit = doTest()
  def testNothing(): Unit = doTest()

  //SCL-11832
  def testCompareOperator(): Unit = doTest()
}
