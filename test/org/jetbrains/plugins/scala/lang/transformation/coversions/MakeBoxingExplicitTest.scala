package org.jetbrains.plugins.scala.lang.transformation
package coversions

import org.jetbrains.plugins.scala.lang.transformation.conversions.MakeBoxingExplicit

/**
  * @author Pavel Fatin
  */
class MakeBoxingExplicitTest extends TransformerTest(new MakeBoxingExplicit()) {
  def testPrimitiveToAny() = check(
    "val v: Any = 1",
    "val v: Any = scala.runtime.BoxesRunTime.boxToInteger(1)"
  )

  def testPrimitiveToAnyVal() = check(
    "val v: AnyVal = 1",
    "val v: AnyVal = scala.runtime.BoxesRunTime.boxToInteger(1)"
  )

  def testPrimitiveToAnyRef() = check(
    "val v: AnyRef = 1", // implicit conversion to Integer
    "val v: AnyRef = 1"
  )

  def testPrimitiveToPrimitive() = check(
    "val v: Int = 1",
    "val v: Int = 1"
  )

  def testAnyValToAnyVal() = check(
    "val v: AnyVal = 1.asInstanceOf[AnyVal]",
    "val v: AnyVal = 1.asInstanceOf[AnyVal]"
  )

  def testObjectToAny() = check(
    "val v: Any = A",
    "val v: Any = A"
  )

  def testSpecializedParameter() = check(
    "class C[@specialized T] {" +
      "def f(p: T) {}",
    "  f(1)",
    "  f(1)",
    "}"
  )

  def testMatchedSpecializedParameter() = check(
    "class C[@specialized(Int) T] {" +
      "def f(p: T) {}",
    "  f(1)",
    "  f(1)",
    "}"
  )

  def testUnmatchedSpecializedParameter() = check(
    "class C[@specialized(Double) T] {" +
      "def f(p: T) {}",
    "  f(1)",
    "  f(scala.runtime.BoxesRunTime.boxToInteger(1))",
    "}"
  )
}
