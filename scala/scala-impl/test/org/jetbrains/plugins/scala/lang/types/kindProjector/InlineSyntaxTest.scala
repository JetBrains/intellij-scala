package org.jetbrains.plugins.scala.lang.types.kindProjector

class InlineSyntaxTest extends KindProjectorTestBase {
  override def folderPath = super.folderPath + "inlineSyntax/"

  def testHigherKind(): Unit  = doTest()
  def testSimple(): Unit      = doTest()
  def testThreeParams(): Unit = doTest()
}
