package org.jetbrains.plugins.scala.lang.types.kindProjector

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/1/15
 */
class InlineSyntaxTest extends KindProjectorTestBase {
  override def folderPath = super.folderPath + "inlineSyntax/"

  def testHigherKind() = doTest()

  def testSimple() = doTest()

  def testThreeParams() = doTest()
}
