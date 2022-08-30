package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class CompileTimeOpsTest extends ScalaLightCodeInsightFixtureTestCase {
  private final val AnyOps =     "import scala.compiletime.ops.any.*; "
  private final val BooleanOps = "import scala.compiletime.ops.boolean.*; "
  private final val IntOps =     "import scala.compiletime.ops.int.*; "
  private final val StringOps =  "import scala.compiletime.ops.string.*; "

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  // General

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

  // Any

  def testAnyEqual(): Unit = assertTypeIs(AnyOps +
    "type T = 1 == true", "false")

  def testAnyNotEqual(): Unit = assertTypeIs(AnyOps +
    "type T = 1 != true", "true")

  // Boolean

  def testBooleanNot(): Unit = assertTypeIs(BooleanOps +
    "type T = ![true]", "false")

  def testBooleanXor(): Unit = assertTypeIs(BooleanOps +
    "type T = true ^ true", "false")

  def testBooleanAnd(): Unit = assertTypeIs(BooleanOps +
    "type T = true && true", "true")

  def testBooleanOr(): Unit = assertTypeIs(BooleanOps +
    "type T = true || false", "true")

  // Int

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

  // String

  def testStringPlus(): Unit = assertTypeIs(StringOps +
    "type T = \"foo\" + \"bar\"", "\"foobar\"")

  private def assertTypeIs(code: String, tpe: String): Unit = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(code)(getProject)
    val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
    val actual = typeElement.`type`().toOption.fold("")(_.presentableText(TypePresentationContext(typeElement)))
    assertEquals(tpe, actual)
  }
}
