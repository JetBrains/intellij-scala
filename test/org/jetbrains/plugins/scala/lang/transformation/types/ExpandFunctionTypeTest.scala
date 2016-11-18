package org.jetbrains.plugins.scala.lang.transformation.types

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandFunctionTypeTest extends TransformerTest(new ExpandFunctionType()) {
  def testSingleArgument() = check(
    "val v: A => B",
    "val v: Function1[A, B]"
  )

  def testParens() = check(
    "val v: (A) => B",
    "val v: Function1[A, B]"
  )

  def testMultipleArguments() = check(
    "val v: (A, B) => C",
    "val v: Function2[A, B, C]"
  )

  def testExplicit() = check(
    "val v: Function1[A, B]",
    "val v: Function1[A, B]"
  )
}
