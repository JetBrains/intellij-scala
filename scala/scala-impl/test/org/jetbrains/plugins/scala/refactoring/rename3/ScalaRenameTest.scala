package org.jetbrains.plugins.scala
package refactoring.rename3

class ScalaRenameTest extends ScalaRenameTestBase {

  def testObjectAndTraitToOpChars(): Unit = doTest("+++")

  def testObjectAndTrait(): Unit = doTest()

  def testObjectAndClass(): Unit = doTest()

  def testObjectAndClassToOpChars(): Unit = doTest("+++")

  def testObjectAndClassToBackticked(): Unit = doTest("`a`")

  def testPrivateObjectAndClass(): Unit = doTest()

  def testPrivateObjectAndPrivateClass(): Unit = doTest()

  def testObjectAndPrivateClass(): Unit = doTest()

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

class Scala3RenameTest extends ScalaRenameTestBase {
  override def supportedIn(v: ScalaVersion): Boolean = v >= LatestScalaVersions.Scala_3_0

  def testTopLevelMethod(): Unit = doTest()

  def testObjectEndMarker(): Unit = doTest()

  def testTraitAndCompanionObjectEndMarker(): Unit = doTest()

  def testEnumAndCompanionObjectEndMarker(): Unit = doTest()

  def testClassAndCompanionObjectEndMarker(): Unit = doTest()

  def testClassAuxConstructorEndMarker(): Unit = doTest()

  def testMethodEndMarker(): Unit = doTest()

  def testOverriddenMethodEndMarker(): Unit = doTest()

  def testOverriddenMethodFromJavaEndMarker(): Unit = doTest()

  def testShadowedValEndMarker(): Unit = doTest()

  def testValueBindingEndMarker(): Unit = doTest("nameAfterRename")

  def testGivenAliasEndMarker(): Unit = doTest()

  def testGivenDefinitionEndMarker(): Unit = doTest()

  // SCL-20145
  def testPackageEndMarker(): Unit = doTest()

  def testPackageEndMarker2(): Unit = doTest()

  def testPackageEndMarker3(): Unit = doTest()

  def testUsageInImportBecomingKeyword(): Unit = doTest("given")
}
