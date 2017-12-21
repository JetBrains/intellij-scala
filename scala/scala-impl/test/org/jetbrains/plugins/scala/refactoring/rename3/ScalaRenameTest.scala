package org.jetbrains .plugins.scala
package refactoring.rename3

/**
 * Nikolay.Tropin
 * 9/13/13
 */
class ScalaRenameTest extends ScalaRenameTestBase {

  def testObjectAndTraitToOpChars(): Unit = doTest("+++")

  def testObjectAndTrait(): Unit = doTest()

  def testObjectAndClass(): Unit = doTest()

  def testObjectAndClassToOpChars(): Unit = doTest("+++")

  def testObjectAndClassToBackticked(): Unit = doTest("`a`")

  def testValInClass(): Unit = doTest()

  def testValInTrait(): Unit = doTest()

  def testVarAndSetters(): Unit = doTest()

  def testSettersWithoutVar(): Unit = {
      try {doTest()}
      catch {
        case e: RuntimeException if e.getMessage endsWith "is not an identifier." =>
      }
    }

  def testSettersWithoutVar2(): Unit = {
    try {doTest("NameAfterRename_=")}
    catch {
      case e: RuntimeException if e.getMessage endsWith "is not an identifier." =>
    }
  }

  def testOverriddenVal(): Unit = doTest()

  def testOverriddenClassParameter(): Unit = doTest()

  def testOverrideDef(): Unit = doTest()

  def testMethodArgument(): Unit = doTest()

  def testMultipleBaseMembers(): Unit = doTest()

  def testTypeAlias(): Unit = doTest()

  def testOverriddenFromJava(): Unit = doTest()

  def testMethodSameAsJavaKeyword(): Unit = doTest()

  def testParamSameAsJavaKeyword(): Unit = doTest()
  
  def testObjectImport(): Unit = doTest()

  def testPrivatePackageClassInheritor(): Unit = doTest()

  def testPrivateSamePackage(): Unit = doTest()

  def testPrivateMemberSamePackage(): Unit = doTest()
}
