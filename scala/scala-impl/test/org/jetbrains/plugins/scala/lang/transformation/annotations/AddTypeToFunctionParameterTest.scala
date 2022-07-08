package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToFunctionParameterTest extends TransformerTest(new AddTypeToFunctionParameter()) {

  override protected val header: String =
    """
     object O {
       def apply(v: A => Unit) {}
     }
  """

  def testImplicitType(): Unit = check(
    before = "O(p => ())",
    after = "O((p: A) => ())"
  )()

  def testSimpleNameBinding(): Unit = check(
    before = "X(p => ())",
    after = "X((p: Source) => ())"
  )(header =
    s"""
     ${TransformationTest.ScalaSourceHeader}
     object X { def apply(f: Source => Unit) {} }
    """)

  def testExplicitType(): Unit = check(
    before = "O((p: A) => ())",
    after = "O((p: A) => ())"
  )()

  def testMethodParameter(): Unit = check(
    before = "def f(p: A): Unit = {}",
    after = "def f(p: A): Unit = {}"
  )()

  def testClassParameter(): Unit = check(
    before = "class T(p: A) {}",
    after = "class T(p: A) {}"
  )()
}
