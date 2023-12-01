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
  private final val DoubleOps =  "import scala.compiletime.ops.double.*; "
  private final val FloatOps =   "import scala.compiletime.ops.float.*; "
  private final val LongOps =    "import scala.compiletime.ops.long.*; "
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

  def testDeAliasTypesRecursively(): Unit = assertTypeIs(IntOps +
    """type A1 = 1
      |type A2 = A1
      |
      |type T = A2 + 2
      |""".stripMargin, "3")

  // ToString
  def testToString_IntLiteral_1(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1] == "1"""", "true")

  def testToString_IntLiteral_2(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1] == "2"""", "false")

  def testToString_LongLiteral_1(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1L] == "1"""", "true")

  def testToString_LongLiteral_2(): Unit = assertTypeIs(AnyOps +
    """type T = ToString[1L] == "2"""", "false")

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

  def testAnyNotEvaluated(): Unit = assertTypeIs(AnyOps +
    "type T[C] = C == true", "C == true")

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

  def testBooleanNotEvaluated(): Unit = assertTypeIs(BooleanOps +
    "type T[C] = ![C]", "![C]")

  // SCL-21875
  def testBooleanNotTypeParameter(): Unit = assertTypeIs(BooleanOps +
    """import scala.compiletime.ops.boolean
      |
      |type ![C] = C match
      |  case Boolean => boolean.![C]
      |  case _       => Any
    """.stripMargin,
    "C match { case Boolean => boolean.![C]; case _$1 => Any }"
  )

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

  def testIntToLong(): Unit = assertTypeIs(IntOps +
    "type T = ToLong[1]", "1L")

  def testIntToFloat(): Unit = assertTypeIs(IntOps +
    "type T = ToFloat[1]", "1.0f")

  def testIntToDouble(): Unit = assertTypeIs(IntOps +
    "type T = ToDouble[1]", "1.0")

  def testIntNumberOfLeadingZeros_Positive(): Unit = {
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[0]", "32")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[1]", "31")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[2]", "30")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[3]", "30")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[4]", "29")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[5]", "29")

    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[510]", "23")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[511]", "23")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[512]", "22")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[513]", "22")

    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[2147483647]", "1")  //Int.MaxValue
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[2147483646]", "1")  //Int.MaxValue - 1
  }

  def testIntNumberOfLeadingZeros_Negative(): Unit = {
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-0]", "32")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-1]", "0")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-2]", "0")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-64]", "0")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-65]", "0")
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-2147483648]", "0") //Int.MinValue
    assertTypeIs(IntOps + "type T = NumberOfLeadingZeros[-2147483647]", "0") //Int.MaxValue + 1
  }

  def testIntNotEvaluated(): Unit = assertTypeIs(IntOps +
    "type T[C] = Abs[C]", "Abs[C]")

  //
  // Long
  //
  def testLongS(): Unit = assertTypeIs(LongOps +
    "type T = S[1L]", "2L")

  def testLongS_IntMax(): Unit = assertTypeIs(LongOps +
    "type T = S[2147483647L]", "2147483648L")

  def testLongS_LongMaxMinus1(): Unit = assertTypeIs(LongOps +
    "type T = S[9223372036854775806L]", "9223372036854775807L")

  def testLongS_LongMax(): Unit = assertTypeIs(LongOps +
    "type T = S[9223372036854775807L]", "-9223372036854775808L")

  def testLongPlus(): Unit = assertTypeIs(LongOps +
    "type T = 1L + 2L", "3L")

  def testLongMinus(): Unit = assertTypeIs(LongOps +
    "type T = 3L - 1L", "2L")

  def testLongMultiply(): Unit = assertTypeIs(LongOps +
    "type T = 2L * 3L", "6L")

  def testLongDivide(): Unit = assertTypeIs(LongOps +
    "type T = 6L / 3L", "2L")

  def testLongRemainder(): Unit = assertTypeIs(LongOps +
    "type T = 6L % 4L", "2L")

  def testLongShiftLeft(): Unit = assertTypeIs(LongOps +
    "type T = 2L << 1L", "4L")

  def testLongShiftRight(): Unit = assertTypeIs(LongOps +
    "type T = 4L >> 1L", "2L")

  def testLongShiftRightZero(): Unit = assertTypeIs(LongOps +
    "type T = 10L >>> 1L", "5L")

  def testLongXor(): Unit = assertTypeIs(LongOps +
    "type T = 2147483647L ^ 2147483646L", "1L")

  def testLongLt(): Unit = assertTypeIs(LongOps +
    "type T = 1L < 2L", "true")

  def testLongGt(): Unit = assertTypeIs(LongOps +
    "type T = 1L > 2L", "false")

  def testLongGtEq(): Unit = assertTypeIs(LongOps +
    "type T = 1L >= 1L", "true")

  def testLongLtEq(): Unit = assertTypeIs(LongOps +
    "type T = 1L <= 1L", "true")

  def testLongBitwiseAnd(): Unit = assertTypeIs(LongOps +
    "type T = BitwiseAnd[1L, 3L]", "1L")

  def testLongBitwiseOr(): Unit = assertTypeIs(LongOps +
    "type T = BitwiseOr[1L, 2L]", "3L")

  def testLongAbs(): Unit = assertTypeIs(LongOps +
    "type T = Abs[-1L]", "1L")

  def testLongNegate(): Unit = assertTypeIs(LongOps +
    "type T = Negate[-1L]", "1L")

  def testLongMin(): Unit = assertTypeIs(LongOps +
    "type T = Min[1L, 2L]", "1L")

  def testLongMax(): Unit = assertTypeIs(LongOps +
    "type T = Max[1L, 2L]", "2L")

  def testLongToInt(): Unit = assertTypeIs(LongOps +
    "type T = ToInt[1L]", "1")

  def testLongToInt_IntOverflowedValue(): Unit = assertTypeIs(LongOps +
    "type T = ToInt[9223372036854775804L]", "-4")

  def testLongToFloat(): Unit = assertTypeIs(LongOps +
    "type T = ToFloat[1L]", "1.0f")

  def testLongToFloat_LongMaxValue(): Unit = assertTypeIs(LongOps +
    "type T = ToFloat[9223372036854775807L]", "9.223372E18f")

  def testLongToDouble(): Unit = assertTypeIs(LongOps +
    "type T = ToDouble[1L]", "1.0")

  def testLongToDouble_LongMaxValue(): Unit = assertTypeIs(LongOps +
    "type T = ToDouble[9223372036854775807L]", "9.223372036854776E18")

  def testLongNumberOfLeadingZeros_Positive(): Unit = {
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[0L]", "64")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[1L]", "63")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[2L]", "62")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[3L]", "62")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[4L]", "61")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[5L]", "61")

    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[510L]", "55")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[511L]", "55")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[512L]", "54")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[513L]", "54")

    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[9223372036854775807L]", "1")  //Long.MaxValue
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[9223372036854775806L]", "1")  //Long.MaxValue - 1
  }

  def testLongNumberOfLeadingZeros_Negative(): Unit = {
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-0L]", "64")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-1L]", "0")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-2L]", "0")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-64L]", "0")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-65L]", "0")
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-9223372036854775808L]", "0") //Long.MinValue
    assertTypeIs(LongOps + "type T = NumberOfLeadingZeros[-9223372036854775807L]", "0") //Long.MaxValue + 1
  }

  def testLongNotEvaluated(): Unit = assertTypeIs(LongOps +
    "type T[C] = Abs[C]", "Abs[C]")

  //
  // Float
  //

  def testFloatPlus(): Unit = assertTypeIs(FloatOps +
    "type T = 1f + 2f", "3.0f")

  def testFloatMinus(): Unit = assertTypeIs(FloatOps +
    "type T = 3f - 1f", "2.0f")

  def testFloatMultiply(): Unit = assertTypeIs(FloatOps +
    "type T = 2f * 3f", "6.0f")

  def testFloatDivide(): Unit = assertTypeIs(FloatOps +
    "type T = 6f / 3f", "2.0f")

  def testFloatRemainder(): Unit = assertTypeIs(FloatOps +
    "type T = 6f % 4f", "2.0f")

  def testFloatLt(): Unit = assertTypeIs(FloatOps +
    "type T = 1f < 2f", "true")

  def testFloatGt(): Unit = assertTypeIs(FloatOps +
    "type T = 1f > 2f", "false")

  def testFloatGtEq(): Unit = assertTypeIs(FloatOps +
    "type T = 1f >= 1f", "true")

  def testFloatLtEq(): Unit = assertTypeIs(FloatOps +
    "type T = 1f <= 1f", "true")

  def testFloatAbs(): Unit = assertTypeIs(FloatOps +
    "type T = Abs[-1f]", "1.0f")

  def testFloatNegate(): Unit = assertTypeIs(FloatOps +
    "type T = Negate[-1f]", "1.0f")

  def testFloatMin(): Unit = assertTypeIs(FloatOps +
    "type T = Min[1f, 2f]", "1.0f")

  def testFloatMax(): Unit = assertTypeIs(FloatOps +
    "type T = Max[1f, 2f]", "2.0f")

  def testFloatToLong(): Unit = assertTypeIs(FloatOps +
    "type T = ToLong[1f]", "1L")

  def testFloatToInt(): Unit = assertTypeIs(FloatOps +
    "type T = ToInt[1f]", "1")

  def testFloatToDouble(): Unit = assertTypeIs(FloatOps +
    "type T = ToDouble[1f]", "1.0")

  def testFloatNotEvaluated(): Unit = assertTypeIs(FloatOps +
    "type T[C] = Abs[C]", "Abs[C]")

  //
  // Double
  //
  def testDoublePlus(): Unit = assertTypeIs(DoubleOps +
    "type T = 1d + 2d", "3.0")

  def testDoubleMinus(): Unit = assertTypeIs(DoubleOps +
    "type T = 3d - 1d", "2.0")

  def testDoubleMultiply(): Unit = assertTypeIs(DoubleOps +
    "type T = 2d * 3d", "6.0")

  def testDoubleDivide(): Unit = assertTypeIs(DoubleOps +
    "type T = 6d / 3d", "2.0")

  def testDoubleRemainder(): Unit = assertTypeIs(DoubleOps +
    "type T = 6d % 4d", "2.0")

  def testDoubleLt(): Unit = assertTypeIs(DoubleOps +
    "type T = 1d < 2d", "true")

  def testDoubleGt(): Unit = assertTypeIs(DoubleOps +
    "type T = 1d > 2d", "false")

  def testDoubleGtEq(): Unit = assertTypeIs(DoubleOps +
    "type T = 1d >= 1d", "true")

  def testDoubleLtEq(): Unit = assertTypeIs(DoubleOps +
    "type T = 1d <= 1d", "true")

  def testDoubleAbs(): Unit = assertTypeIs(DoubleOps +
    "type T = Abs[-1d]", "1.0")

  def testDoubleNegate(): Unit = assertTypeIs(DoubleOps +
    "type T = Negate[-1d]", "1.0")

  def testDoubleMin(): Unit = assertTypeIs(DoubleOps +
    "type T = Min[1d, 2d]", "1.0")

  def testDoubleMax(): Unit = assertTypeIs(DoubleOps +
    "type T = Max[1d, 2d]", "2.0")

  def testDoubleToLong(): Unit = assertTypeIs(DoubleOps +
    "type T = ToLong[1d]", "1L")

  def testDoubleToInt(): Unit = assertTypeIs(DoubleOps +
    "type T = ToInt[1d]", "1")

  def testDoubleToFloat(): Unit = assertTypeIs(DoubleOps +
    "type T = ToFloat[1d]", "1.0f")

  def testDoubleNotEvaluated(): Unit = assertTypeIs(DoubleOps +
    "type T[C] = Abs[C]", "Abs[C]")

  //
  // String
  //

  def testStringPlus(): Unit = assertTypeIs(StringOps +
    "type T = \"foo\" + \"bar\"", "\"foobar\"")

  def testStringLength(): Unit = assertTypeIs(StringOps +
    """type T = Length["hello"]""", "5")

  def testStringChatAt(): Unit = assertTypeIs(StringOps +
    """type T = CharAt["hello", 4]""", "'o'")

  def testStringChatAt_Bad_IndexLargerThenLength(): Unit = assertTypeIs(StringOps +
    """type T = CharAt["hello", 5]""", """CharAt["hello", 5]""")

  def testStringChatAt_Bad_NegativeIndex(): Unit = assertTypeIs(StringOps +
    """type T = CharAt["hello", -1]""", """CharAt["hello", -1]""")

  def testStringSubstring(): Unit = assertTypeIs(StringOps +
    """type T = Substring["hello", 2, 5]""", """"llo"""")

  def testStringSubstring_EmptyRange(): Unit = assertTypeIs(StringOps +
    """type T = Substring["hello", 2, 2]""", """""""")

  def testStringSubstring_BadRange_1(): Unit = assertTypeIs(StringOps +
    """type T = Substring["hello", 2, 1]""", """Substring["hello", 2, 1]""")

  def testStringSubstring_BadRange_2(): Unit = assertTypeIs(StringOps +
    """type T = Substring["hello", -1, 5]""", """Substring["hello", -1, 5]""")

  def testStringSubstring_BadRange_3(): Unit = assertTypeIs(StringOps +
    """type T = Substring["hello", 2, 6]""", """Substring["hello", 2, 6]""")

  def testStringMatches_True(): Unit = assertTypeIs(StringOps +
    """type T = Matches["unhappy", "un.*"]""", "true")

  def testStringMatches_False(): Unit = assertTypeIs(StringOps +
    """type T = Matches["unhappy", "ab.*"]""", "false")

  def testStringMatches_Bad_WrongPattern(): Unit = assertTypeIs(StringOps +
    """type T = Matches["unhappy", "{"]""", """Matches["unhappy", "{"]""")

  def testStringNotEvaluated(): Unit = assertTypeIs(StringOps +
    "type T[C] = Length[C]", "Length[C]")

  private def assertTypeIs(code: String, tpe: String): Unit = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(code, ScalaFeatures.onlyByVersion(version))(getProject)
    val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
    val actual = typeElement.`type`().toOption.fold("")(_.presentableText(TypePresentationContext(typeElement)))
    assertEquals(tpe, actual)
  }
}
