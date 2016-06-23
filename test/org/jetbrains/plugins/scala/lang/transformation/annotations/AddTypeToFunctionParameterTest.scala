package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToFunctionParameterTest extends TransformerTest(AddTypeToFunctionParameter,
  """
     object O {
       def apply(v: A => Unit) {}
     }
  """) {

  def testImplicitType() = check(
    "O(p => ())",
    "O((p: A) => ())"
  )

  def testSimpleNameBinding() = check(
    """
     import scala.io.Source
     object X { def apply(f: Source => Unit) {} }
    """,
    "X(p => ())",
    "X((p: Source) => ())"
  )

  def testExplicitType() = check(
    "O((p: A) => ())",
    "O((p: A) => ())"
  )

  def testMethodParameter() = check(
    "def f(p: A) {}",
    "def f(p: A) {}"
  )

  def testClassParameter() = check(
    "class T(p: A) {}",
    "class T(p: A) {}"
  )
}
