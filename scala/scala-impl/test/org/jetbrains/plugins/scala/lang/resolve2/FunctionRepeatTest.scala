package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionRepeatTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "repeat"

  def testArraya(): Unit = doTest()
  def testArrayRaw(): Unit = doTest()
  def testEmpty(): Unit = doTest()
  def testNone(): Unit = doTest()
  def testOne(): Unit = doTest()
  def testTwo(): Unit = doTest()
  def testIncompatibleArraya(): Unit = doTest()
  def testIncompatibleOne(): Unit = doTest()
  def testIncompatibleTwo(): Unit = doTest()
}
