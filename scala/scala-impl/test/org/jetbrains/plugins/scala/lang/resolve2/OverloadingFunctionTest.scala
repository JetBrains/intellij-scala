package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class OverloadingFunctionTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "overloading" / "functions"

  def testFunction1(): Unit = doTest()
  def testFunction2(): Unit = doTest()
  def testFunction3(): Unit = doTest()
  def testFunction4(): Unit = doTest()
  def testFunction5(): Unit = doTest()
  def testFunction6(): Unit = doTest()
}
