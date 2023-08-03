package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class CompileTimeOpsTest extends ScalaLightCodeInsightFixtureTestCase {
  private final val AnyOps =     "import scala.compiletime.ops.any.*; "
  private final val BooleanOps = "import scala.compiletime.ops.boolean.*; "
  private final val IntOps =     "import scala.compiletime.ops.int.*; "
  private final val StringOps =  "import scala.compiletime.ops.string.*; "

  private final val CommonAliases =
    """type AliasTo1 = 1
      |type AliasToTrue = true
      |type AliasToFalse = false
      |type AliasToInt = Int
      |type AliasToString = String
      |""".stripMargin.trim

  override protected def setUp(): Unit = {
    super.setUp()

    getFixture.addFileToProject("commons.scala", CommonAliases)
  }

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  //
  // General
  //

  def testSingleton(): Unit = assertTypeIs(IntOps +
    "type T = 1 + 2", "3")

  def testNotResolved(): Unit = assertTypeIs(
    "type T = 1 + 2", "")

  def testDifferentOperator(): Unit = assertTypeIs(
    "class +[L, R]; type T = 1 + 2", "1 + 2")

  def testTypeMismatch(): Unit = assertTypeIs(IntOps +
    "type T = 1 + String", "1 + String")

  def testNotSingleton(): Unit = assertTypeIs(IntOps +
    "type T = 1 + Int", "1 + Int")

  def testTypeVariable(): Unit = assertTypeIs(IntOps +
    "type T[A <: Int] = 1 + A", "1 + A")

  //
  // Any
  //

  // ==
  def testAnyEqual(): Unit = assertTypeIs(AnyOps +
    "type T = 1 == true", "false")

  def testAnyEqual_StringLiteral_1(): Unit = assertTypeIs(AnyOps +
    "type T = \"42\" == \"42\"", "true")

  def testAnyEqual_StringLiteral_2(): Unit = assertTypeIs(AnyOps +
    "type T = \"42\" == \"23\"", "false")

  def testAnyEqual_AliasToTrue(): Unit = assertTypeIs(AnyOps +
    "type T = true == AliasToTrue", "true")

  def testAnyEqual_AliasToFalse(): Unit = assertTypeIs(AnyOps +
    "type T = true == AliasToFalse", "false")

  def testAnyEqual_NonLiteralTypes(): Unit = assertTypeIs(AnyOps +
    "type T = String == String", "String == String")

  // !=
  def testAnyNotEqual(): Unit = assertTypeIs(AnyOps +
    "type T = 1 != true", "true")

  // IsConst
  def testAnyIsConst_NumericLiteral(): Unit = assertTypeIs(AnyOps +
    "type T = IsConst[1]", "true")

  def testAnyIsConst_StringLiteral(): Unit = assertTypeIs(AnyOps +
    """type T = IsConst["hi"]""", "true")

  def testAnyIsConst_BooleanLiteral_1(): Unit = assertTypeIs(AnyOps +
    "type T = IsConst[true]", "true")

  def testAnyIsConst_BooleanLiteral_2(): Unit = assertTypeIs(AnyOps +
    "type T = IsConst[false]", "true")

  def testAnyIsConst_Any(): Unit = assertTypeIs(AnyOps +
    "type T = IsConst[Any]", "false")

  def testAnyIsConst_String(): Unit = assertTypeIs(AnyOps +
    "type T = IsConst[String]", "false")

  def testAnyIsConst_SingletonObjectType(): Unit = {
    getFixture.addFileToProject("dummy.scala",
      "object MyObject")
    assertTypeIs(AnyOps +
      "type T = IsConst[MyObject.type]", "false")
  }

  def testAnyIsConst_AliasToLiteralType(): Unit = assertTypeIs(AnyOps +
    """type T = IsConst[AliasTo1]""", "true")

  def testAnyIsConst_AliasToNonLiteralType(): Unit = assertTypeIs(AnyOps +
    """type T = IsConst[AliasToString]""", "false")

  // ToString
  def testToString_IntLiteral_1(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1] == "1"""", "true")

  def testToString_IntLiteral_2(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1] == "2"""", "false")

  def testToString_BooleanLiteral_1(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[true] == "true"""", "true")

  def testToString_BooleanLiteral_2(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[true] == "false"""", "false")

  def testToString_StringLiteral_1(): Unit = assertTypeIs(AnyOps +
    """type T = ToString["42"] == "42"""", "true")

  def testToString_StringLiteral_2(): Unit = assertTypeIs(AnyOps +
    """type T = ToString["42"] == "23"""", "false")

  def testToString_NonLiteralType(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[AliasToInt] == "1"""", """ToString[AliasToInt] == "1"""")

  //
  // Boolean
  //

  def testBooleanNot(): Unit = assertTypeIs(BooleanOps +
    "type T = ![true]", "false")

  def testBooleanXor(): Unit = assertTypeIs(BooleanOps +
    "type T = true ^ true", "false")

  def testBooleanAnd(): Unit = assertTypeIs(BooleanOps +
    "type T = true && true", "true")

  def testBooleanOr(): Unit = assertTypeIs(BooleanOps +
    "type T = true || false", "true")

  //
  // Int
  //

  def testIntS(): Unit = assertTypeIs(IntOps +
    "type T = S[1]", "2")

  def testIntPlus(): Unit = assertTypeIs(IntOps +
    "type T = 1 + 2", "3")

  def testIntMinus(): Unit = assertTypeIs(IntOps +
    "type T = 3 - 1", "2")

  def testIntMultiply(): Unit = assertTypeIs(IntOps +
    "type T = 2 * 3", "6")

  def testIntDivide(): Unit = assertTypeIs(IntOps +
    "type T = 6 / 3", "2")

  def testIntRemainder(): Unit = assertTypeIs(IntOps +
    "type T = 6 % 4", "2")

  def testIntShiftLeft(): Unit = assertTypeIs(IntOps +
    "type T = 2 << 1", "4")

  def testIntShiftRight(): Unit = assertTypeIs(IntOps +
    "type T = 4 >> 1", "2")

  def testIntShiftRightZero(): Unit = assertTypeIs(IntOps +
    "type T = 10 >>> 1", "5")

  def testIntXor(): Unit = assertTypeIs(IntOps +
    "type T = 2147483647 ^ 2147483646", "1")

  def testIntLt(): Unit = assertTypeIs(IntOps +
    "type T = 1 < 2", "true")

  def testIntGt(): Unit = assertTypeIs(IntOps +
    "type T = 1 > 2", "false")

  def testIntGtEq(): Unit = assertTypeIs(IntOps +
    "type T = 1 >= 1", "true")

  def testIntLtEq(): Unit = assertTypeIs(IntOps +
    "type T = 1 <= 1", "true")

  def testIntBitwiseAnd(): Unit = assertTypeIs(IntOps +
    "type T = BitwiseAnd[1, 3]", "1")

  def testIntBitwiseOr(): Unit = assertTypeIs(IntOps +
    "type T = BitwiseOr[1, 2]", "3")

  def testIntAbs(): Unit = assertTypeIs(IntOps +
    "type T = Abs[-1]", "1")

  def testIntNegate(): Unit = assertTypeIs(IntOps +
    "type T = Negate[-1]", "1")

  def testIntMin(): Unit = assertTypeIs(IntOps +
    "type T = Min[1, 2]", "1")

  def testIntMax(): Unit = assertTypeIs(IntOps +
    "type T = Max[1, 2]", "2")

  def testIntToString(): Unit = assertTypeIs(IntOps +
    "type T = ToString[1]", "\"1\"")

  //
  // String
  //

  def testStringPlus(): Unit = assertTypeIs(StringOps +
    "type T = \"foo\" + \"bar\"", "\"foobar\"")

  private def assertTypeIs(code: String, tpe: String): Unit = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(code, ScalaFeatures.onlyByVersion(version))(getProject)
    val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
    val actual = typeElement.`type`().toOption.fold("")(_.presentableText(TypePresentationContext(typeElement)))
    assertEquals(tpe, actual)
  }
}
