package org.jetbrains.plugins.scala
package lang
package transformation
package types

class ExpandFunctionTypeTest extends TransformerTest(new ExpandFunctionType()) {

  def testSingleArgument(): Unit = check(
    before = "val v: A => B",
    after = "val v: Function1[A, B]"
  )()

  def testParenthesis(): Unit = check(
    before = "val v: (A) => B",
    after = "val v: Function1[A, B]"
  )()

  def testMultipleArguments(): Unit = check(
    before = "val v: (A, B) => C",
    after = "val v: Function2[A, B, C]"
  )()

  def testExplicit(): Unit = check(
    before = "val v: Function1[A, B]",
    after = "val v: Function1[A, B]"
  )()
}
