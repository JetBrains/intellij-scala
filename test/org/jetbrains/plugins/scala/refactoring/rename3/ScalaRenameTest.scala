package org.jetbrains .plugins.scala
package refactoring.rename3

/**
 * Nikolay.Tropin
 * 9/13/13
 */
class ScalaRenameTest extends ScalaRenameTestBase {

  //def testObjectAndTraitToOpChars() = doTest("+++")

  def testObjectAndTrait() = doTest()

  def testObjectAndClass() = doTest()

  //def testObjectAndClassToOpChars() = doTest("+++")

  def testObjectAndClassToBackticked() = doTest("`a`")

  def testValInClass() = doTest()

  def testValInTrait() = doTest()

  def testVarAndSetters() = doTest()

//  def testSettersWithoutVar() = {
  //    try {doTest()}
  //    catch {
  //      case e: RuntimeException if e.getMessage endsWith "is not an identifier." =>
  //    }
  //  }

  def testSettersWithoutVar2() = {
    try {doTest("NameAfterRename_=")}
    catch {
      case e: RuntimeException if e.getMessage endsWith "is not an identifier." =>
    }
  }

  def testOverridenVal() = doTest()

  def testOverridenClassParameter() = doTest()

  def testOverrideDef() = doTest()

  def testMethodArgument() = doTest()
}
