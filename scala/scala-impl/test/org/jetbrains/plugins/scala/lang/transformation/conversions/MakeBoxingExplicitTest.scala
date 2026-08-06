package org.jetbrains.plugins.scala
package lang
package transformation
package conversions

class MakeBoxingExplicitTest extends TransformerTest(new MakeBoxingExplicit()) {

  def testPrimitiveToAny(): Unit = check(
    before = "val v: Any = 1",
    after = "val v: Any = scala.runtime.BoxesRunTime.boxToInteger(1)"
  )()

  def testPrimitiveToAnyVal(): Unit = check(
    before = "val v: AnyVal = 1",
    after = "val v: AnyVal = scala.runtime.BoxesRunTime.boxToInteger(1)"
  )()

  def testPrimitiveToAnyRef(): Unit = check(
    before = "val v: AnyRef = 1", // implicit conversion to Integer
    after = "val v: AnyRef = 1"
  )()

  def testPrimitiveToPrimitive(): Unit = check(
    before = "val v: Int = 1",
    after = "val v: Int = 1"
  )()

  def testAnyValToAnyVal(): Unit = check(
    before = "val v: AnyVal = 1.asInstanceOf[AnyVal]",
    after = "val v: AnyVal = 1.asInstanceOf[AnyVal]"
  )()

  def testObjectToAny(): Unit = check(
    before = "val v: Any = A",
    after = "val v: Any = A"
  )()

  def testSpecializedParameter(): Unit = check(
    before = "  f(1)",
    after = "  f(1)"
  )(
    header = "class C[@specialized T] {def f(p: T): Unit = {}",
    footer = "}"
  )

  def testMatchedSpecializedParameter(): Unit = check(
    before = "  f(1)",
    after = "  f(1)"
  )(
    header = "class C[@specialized(Int) T] {def f(p: T): Unit = {}",
    footer = "}"
  )

  def testUnmatchedSpecializedParameter(): Unit = check(
    before = "  f(1)",
    after = "  f(scala.runtime.BoxesRunTime.boxToInteger(1))"
  )(
    header = "class C[@specialized(Double) T] {def f(p: T): Unit = {}",
    footer = "}"
  )
}
