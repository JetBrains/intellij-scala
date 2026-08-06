package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class PredefClashTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "predef" / "clash"

  def testInherited(): Unit = doTest()
  def testLocal1(): Unit = doTest()
  def testLocal2(): Unit = doTest()
  def testOuterScope(): Unit = doTest()
}
