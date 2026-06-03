package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class InlineSyntaxTest extends KindProjectorTestBase {
  override def folderPath: Path = super.folderPath / "inlineSyntax"

  def testHigherKind(): Unit  = doTest()
  def testSimple(): Unit      = doTest()
  def testThreeParams(): Unit = doTest()
}
