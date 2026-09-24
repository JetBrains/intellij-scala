package org.jetbrains.plugins.scala
package refactoring.rename3

import org.jetbrains.plugins.scala.util.assertions.assertFails
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class ScalaRenameTest extends ScalaRenameTestBase {

  @Test
  def testObjectAndTraitToOpChars(): Unit = doTest("+++")

  @Test
  def testObjectAndTrait(): Unit = doTest()

  @Test
  def testObjectAndClass(): Unit = doTest()

  @Test
  def testObjectAndClassToOpChars(): Unit = doTest("+++")

  @Test
  def testObjectAndClassToBackticked(): Unit = doTest("`a`")

  @Test
  def testObjectAndAbstractTypeInScala2(): Unit = doTest()

  @Test
  def testPrivateObjectAndClass(): Unit = doTest()

  @Test
  def testPrivateObjectAndPrivateClass(): Unit = doTest()

  @Test
  def testObjectAndPrivateClass(): Unit = doTest()

  @Test
  def testValInClass(): Unit = doTest()

  @Test
  def testValInTrait(): Unit = doTest()

  @Test
  def testVarAndSetters(): Unit = doTest()

  @Test
  def testSettersWithoutVar(): Unit = {
      try {doTest()}
      catch {
        case e: RuntimeException if e.getMessage.endsWith("is not an identifier.") =>
      }
    }

  @Test
  def testSettersWithoutVar2(): Unit = {
    try {doTest("NameAfterRename_=")}
    catch {
      case e: RuntimeException if e.getMessage.endsWith("is not an identifier.") =>
    }
  }

  @Test
  def testOverriddenVal(): Unit = doTest()

  @Test
  def testOverriddenClassParameter(): Unit = doTest()

  @Test
  def testOverrideDef(): Unit = doTest()

  @Test
  def testMethodArgument(): Unit = doTest()

  @Test
  def testMultipleBaseMembers(): Unit = doTest()

  @Test
  def testMultipleBaseMembersWithJava(): Unit = doTest()

  @Test
  def testSuperMethodsChain(): Unit = doTest()

  @Test
  def testSuperMethodsChainWithJava(): Unit = doTest()

  @Test
  def testSuperMethodsChainWithJava2(): Unit = doTest()

  @Test
  def testTypeAlias(): Unit = doTest()

  @Test
  def testOverriddenFromJava(): Unit = doTest()

  //FIXME when SCL-25260 is fixed  (or related causing ticket)
  @Test
  def testOverriddenFromBaseJavaClassInScalaTraitDirect(): Unit = assertFails {
    doTest()
  }

  //FIXME when SCL-25260 is fixed  (or related causing ticket)
  @Test
  def testOverriddenFromBaseJavaClassInScalaTraitIndirect(): Unit = assertFails {
    doTest()
  }

  @Test
  def testMethodSameAsJavaKeyword(): Unit = doTest()

  @Test
  def testParamSameAsJavaKeyword(): Unit = doTest()

  @Test
  def testObjectImport(): Unit = doTest()

  @Test
  def testPrivatePackageClassInheritor(): Unit = doTest()

  @Test
  def testPrivateSamePackage(): Unit = doTest()

  @Test
  def testPrivateMemberSamePackage(): Unit = doTest()
}

class Scala3RenameTest extends ScalaRenameTestBase {
  override def supportedIn(v: ScalaVersion): Boolean = v >= LatestScalaVersions.Scala_3_0

  // TODO also from type & type reference
  def testObjectAndAbstractType(): Unit = doTest()

  // TODO also from type & type reference
  def testObjectAndOpaqueType(): Unit = doTest()

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

  def testInterleavedClauseTypeParameter(): Unit = doTest()
}
