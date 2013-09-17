package org.jetbrains .plugins.scala
package refactoring.rename3

/**
 * Nikolay.Tropin
 * 9/13/13
 */
class ScalaRenameTest extends ScalaRenameTestBase {

  def testObjectAndClass() = doTest()

  def testObjectAndClassToOpChars() = doTest("+++")

  def testObjectAndClassToBackticked() = doTest("`a`")

  def testObjectAndTrait() = doTest()

  def testObjectAndTraitToOpChars() = doTest("+++")
}
