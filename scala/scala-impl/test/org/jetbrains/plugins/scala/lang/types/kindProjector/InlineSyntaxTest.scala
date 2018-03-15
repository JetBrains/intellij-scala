package org.jetbrains.plugins.scala.lang.types.kindProjector

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/1/15
 */
class InlineSyntaxTest extends KindProjectorTestBase {
  override def folderPath = super.folderPath + "inlineSyntax/"

  def testHigherKind(): Unit  = doTest()
  def testSimple(): Unit      = doTest()
  def testThreeParams(): Unit = doTest()
}
